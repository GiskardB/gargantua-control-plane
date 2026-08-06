package ai.gargantua.controlplane.registry;

import ai.gargantua.controlplane.registry.model.BundleArtifact;
import ai.gargantua.controlplane.storage.DocumentStore;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Durable {@link Registry} over the {@link DocumentStore}, keyed by the bundle
 * coordinate {@code name@version}. Replaces the in-memory MVP store: artifacts now
 * survive restarts. Versions remain immutable — enforced by the RegistryService.
 */
@Repository
public class JdbcRegistry implements Registry {

    private static final String COLLECTION = "registry";

    private final DocumentStore store;

    public JdbcRegistry(DocumentStore store) {
        this.store = store;
    }

    @Override
    public void save(BundleArtifact artifact) {
        store.put(COLLECTION, artifact.descriptor().coordinate(), artifact);
    }

    @Override
    public void update(BundleArtifact artifact) {
        store.put(COLLECTION, artifact.descriptor().coordinate(), artifact);
    }

    @Override
    public List<BundleArtifact> findAll() {
        return store.list(COLLECTION, BundleArtifact.class);
    }

    @Override
    public List<BundleArtifact> findByName(String name) {
        return findAll().stream()
                .filter(a -> a.descriptor().name().equals(name))
                .toList();
    }

    @Override
    public Optional<BundleArtifact> find(String name, String version) {
        return store.get(COLLECTION, name + "@" + version, BundleArtifact.class);
    }

    @Override
    public boolean exists(String name, String version) {
        return store.exists(COLLECTION, name + "@" + version);
    }
}
