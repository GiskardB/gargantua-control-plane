package ai.gargantua.controlplane.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC-backed {@link DocumentStore} over a single {@code cp_document} table
 * (collection, id, doc). Works unchanged on the embedded H2 (default) and on
 * Postgres (the {@code postgres} profile): the upsert is a portable
 * update-then-insert rather than a dialect-specific {@code MERGE}/{@code ON CONFLICT}.
 *
 * <p>Documents are (de)serialized with a dedicated Jackson 2 mapper — Spring Boot 4
 * serves HTTP with Jackson 3, but persistence stays on the stable Jackson 2 that
 * already parses manifests.
 */
@Repository
public class JdbcDocumentStore implements DocumentStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcDocumentStore(JdbcTemplate jdbc, ObjectMapper documentObjectMapper) {
        this.jdbc = jdbc;
        this.mapper = documentObjectMapper;
    }

    @Override
    @Transactional
    public void put(String collection, String id, Object value) {
        String doc = serialize(value);
        int updated = jdbc.update(
                "UPDATE cp_document SET doc = ? WHERE collection = ? AND id = ?",
                doc, collection, id);
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO cp_document (collection, id, doc) VALUES (?, ?, ?)",
                    collection, id, doc);
        }
    }

    @Override
    public <T> Optional<T> get(String collection, String id, Class<T> type) {
        List<String> docs = jdbc.query(
                "SELECT doc FROM cp_document WHERE collection = ? AND id = ?",
                (rs, n) -> rs.getString(1), collection, id);
        return docs.isEmpty() ? Optional.empty() : Optional.of(deserialize(docs.get(0), type));
    }

    @Override
    public <T> List<T> list(String collection, Class<T> type) {
        return jdbc.query(
                "SELECT doc FROM cp_document WHERE collection = ? ORDER BY id",
                (rs, n) -> deserialize(rs.getString(1), type), collection);
    }

    @Override
    public boolean delete(String collection, String id) {
        return jdbc.update(
                "DELETE FROM cp_document WHERE collection = ? AND id = ?", collection, id) > 0;
    }

    @Override
    public boolean exists(String collection, String id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cp_document WHERE collection = ? AND id = ?",
                Integer.class, collection, id);
        return count != null && count > 0;
    }

    private String serialize(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("cannot serialize document", e);
        }
    }

    private <T> T deserialize(String doc, Class<T> type) {
        try {
            return mapper.readValue(doc, type);
        } catch (Exception e) {
            throw new IllegalStateException("cannot deserialize document of " + type, e);
        }
    }
}
