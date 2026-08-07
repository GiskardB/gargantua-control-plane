package ai.gargantua.controlplane.registry.model;

import ai.gargantua.core.capability.Capability;
import ai.gargantua.core.workload.WorkloadKind;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The Registry's view of a published bundle: the immutable metadata the Control
 * Plane derives from an accepted {@code gargantua.ai/v1} manifest.
 *
 * <p>This is a Registry projection, not the domain's {@link
 * ai.gargantua.core.bundle.BundleDescriptor} — that one models a compiled bundle's
 * provenance (signature, runtime image), whereas this one is what the Control Plane
 * indexes and promotes: the workload kind and the capabilities the manifest declares,
 * plus the checksum over the exact bytes stored in the {@code BlobStore}. The shared
 * vocabulary it is built from ({@link WorkloadKind}, {@link Capability}) comes from
 * agent-core, so the Registry and the Runtime name the same things the same way.
 *
 * @param name         bundle name, taken from the manifest metadata
 * @param version      bundle version, taken from the manifest metadata
 * @param kind         the workload kind the bundle deploys
 * @param checksum     SHA-256 of the manifest bytes, hex-encoded
 * @param capabilities capabilities the bundle declares it provides
 * @param labels       free-form metadata copied from the manifest
 * @param createdAt    when the Control Plane accepted the bundle
 */
public record PublishedBundle(
        String name,
        String version,
        WorkloadKind kind,
        String checksum,
        List<Capability> capabilities,
        Map<String, String> labels,
        Instant createdAt) {

    public PublishedBundle {
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
