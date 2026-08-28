package ai.gargantua.controlplane.storage;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Default {@link BlobStore} that stores manifest YAML and bundle ZIP bytes
 * as JSON documents in the existing {@code cp_document} table via
 * {@link JdbcDocumentStore}. Works identically on embedded H2 and on
 * Postgres — no infrastructure required beyond the JDBC datasource.
 *
 * <p>Blobs are stored in the {@code doc} column (Jackson serializes byte[]
 * as base64). No schema changes are needed.
 *
 * <p>Key format: {@code name/version} (unchanged from existing BlobStore
 * usage). The collection is derived from the key suffix:
 *   - ends with {@code /manifest.yaml} → collection {@code manifest}
 *   - ends with {@code /bundle.gbundle} → collection {@code bundle}
 */
@Component
public class PostgresBlobStore implements BlobStore {

    private final JdbcDocumentStore docStore;

    public PostgresBlobStore(JdbcDocumentStore docStore) {
        this.docStore = docStore;
    }

    @Override
    public void put(String key, byte[] bytes) {
        docStore.put(collection(key), key, bytes);
    }

    @Override
    public Optional<byte[]> get(String key) {
        return docStore.get(collection(key), key, byte[].class);
    }

    @Override
    public boolean exists(String key) {
        return docStore.exists(collection(key), key);
    }

    @Override
    public void delete(String key) {
        docStore.delete(collection(key), key);
    }

    /** Map blob key to a cp_document collection name. */
    private static String collection(String key) {
        if (key.endsWith("/manifest.yaml")) {
            return "manifest";
        }
        if (key.endsWith("/bundle.gbundle")) {
            return "bundle";
        }
        throw new IllegalArgumentException("unknown blob key format: " + key);
    }
}