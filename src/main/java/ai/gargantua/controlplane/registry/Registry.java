package ai.gargantua.controlplane.registry;

import ai.gargantua.controlplane.registry.model.BundleArtifact;
import java.util.List;
import java.util.Optional;

/**
 * Store of published bundle artifacts. A port (ADR-004): the MVP keeps everything
 * in memory, but the contract is what a Postgres-backed implementation would honour
 * unchanged. Versions are immutable once published.
 */
public interface Registry {

    /** Persist a newly published artifact. */
    void save(BundleArtifact artifact);

    /** Replace an existing artifact in place (used by promotion). */
    void update(BundleArtifact artifact);

    /** All artifacts, newest first is not guaranteed; ordering is caller's concern. */
    List<BundleArtifact> findAll();

    /** All versions of a given bundle name. */
    List<BundleArtifact> findByName(String name);

    /** A specific version of a bundle. */
    Optional<BundleArtifact> find(String name, String version);

    /** Whether a specific version already exists. */
    boolean exists(String name, String version);
}
