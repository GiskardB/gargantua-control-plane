package ai.gargantua.controlplane.storage;

import java.util.Optional;

/**
 * Content-addressable store for the raw bundle manifest bytes.
 *
 * <p>A port (ADR-004 style): the MVP backs it with the local filesystem, but the
 * contract is deliberately narrow so a MinIO/S3 backend can replace it without
 * touching the Registry. Blobs are immutable and keyed by their coordinate.
 */
public interface BlobStore {

    /**
     * Persist the manifest bytes for a bundle coordinate. Overwrites are the
     * caller's concern to prevent (the Registry enforces version immutability).
     *
     * @param key   opaque storage key, typically {@code name/version}
     * @param bytes the raw manifest content
     */
    void put(String key, byte[] bytes);

    /** Fetch previously stored bytes, if present. */
    Optional<byte[]> get(String key);

    /** Whether a blob exists for the key. */
    boolean exists(String key);
}
