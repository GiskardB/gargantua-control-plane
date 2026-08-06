package ai.gargantua.controlplane.registry.model;

import ai.gargantua.controlplane.domain.BundleDescriptor;
import java.util.EnumSet;
import java.util.Set;

/**
 * A published bundle version as the Registry tracks it: its immutable
 * {@link BundleDescriptor} plus the mutable set of environments it has been
 * promoted to. The manifest bytes live in the {@code BlobStore}, keyed by the
 * descriptor's coordinate.
 */
public record BundleArtifact(
        BundleDescriptor descriptor,
        Set<Environment> environments) {

    public BundleArtifact {
        environments = environments == null || environments.isEmpty()
                ? EnumSet.noneOf(Environment.class)
                : EnumSet.copyOf(environments);
    }

    public static BundleArtifact of(BundleDescriptor descriptor) {
        return new BundleArtifact(descriptor, EnumSet.noneOf(Environment.class));
    }

    /** Return a copy promoted to the given environment (descriptor unchanged). */
    public BundleArtifact promotedTo(Environment env) {
        EnumSet<Environment> next = EnumSet.copyOf(
                environments.isEmpty() ? EnumSet.noneOf(Environment.class) : environments);
        next.add(env);
        return new BundleArtifact(descriptor, next);
    }
}
