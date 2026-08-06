package ai.gargantua.controlplane.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable metadata about a published bundle — the declarative package the
 * Runtime later applies. Mirrors the Bundle object in the Gargantua domain model.
 *
 * <p>A bundle is data, never executable code (ADR-003): the descriptor records what
 * it is, what it exposes and how to verify it, but the manifest bytes themselves
 * live in the {@code BlobStore}.
 *
 * @param name         bundle name, taken from the manifest metadata
 * @param version      bundle version, taken from the manifest metadata
 * @param kind         the workload kind the bundle deploys
 * @param checksum     SHA-256 of the manifest bytes, hex-encoded
 * @param capabilities capabilities the bundle declares it provides
 * @param labels       free-form metadata copied from the manifest
 * @param createdAt    when the Control Plane accepted the bundle
 */
public record BundleDescriptor(
        String name,
        String version,
        WorkloadKind kind,
        String checksum,
        List<Capability> capabilities,
        Map<String, String> labels,
        Instant createdAt) {

    public BundleDescriptor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("bundle name must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("bundle version must not be blank");
        }
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        labels = labels == null ? Map.of() : Map.copyOf(labels);
    }

    /** Registry coordinate: {@code name@version}. */
    public String coordinate() {
        return name + "@" + version;
    }
}
