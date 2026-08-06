package ai.gargantua.controlplane.storage;

import java.util.List;
import java.util.Optional;

/**
 * A minimal document store: each aggregate is persisted as one JSON row, keyed by
 * ({@code collection}, {@code id}). A port (ADR-004) so the subsystems' durable
 * backends stay behind one narrow contract — the MVP backs it with an embedded H2,
 * and the {@code postgres} profile swaps in Postgres without touching callers.
 *
 * <p>Deliberately document-oriented rather than relational: the domain aggregates
 * are immutable records with nested collections, so storing them as JSON keeps the
 * domain pure and the migration to Postgres a datasource change, not a remodelling.
 */
public interface DocumentStore {

    /** Insert or replace the document for ({@code collection}, {@code id}). */
    void put(String collection, String id, Object value);

    /** Fetch and deserialize a document, if present. */
    <T> Optional<T> get(String collection, String id, Class<T> type);

    /** All documents in a collection, deserialized. */
    <T> List<T> list(String collection, Class<T> type);

    /** Remove a document; returns whether a row existed. */
    boolean delete(String collection, String id);

    /** Whether a document exists for ({@code collection}, {@code id}). */
    boolean exists(String collection, String id);
}
