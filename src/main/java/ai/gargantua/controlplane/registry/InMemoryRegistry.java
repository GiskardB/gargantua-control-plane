package ai.gargantua.controlplane.registry;

import ai.gargantua.controlplane.registry.model.BundleArtifact;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * In-memory {@link Registry} for the MVP. Keyed by {@code name@version}; thread-safe
 * via a concurrent map so it survives concurrent publishes within one node. State is
 * lost on restart — deliberately, until a durable backend replaces it.
 */
@Repository
public class InMemoryRegistry implements Registry {

    private final Map<String, BundleArtifact> byCoordinate = new ConcurrentHashMap<>();

    @Override
    public void save(BundleArtifact artifact) {
        byCoordinate.put(key(artifact), artifact);
    }

    @Override
    public void update(BundleArtifact artifact) {
        byCoordinate.put(key(artifact), artifact);
    }

    @Override
    public List<BundleArtifact> findAll() {
        return List.copyOf(byCoordinate.values());
    }

    @Override
    public List<BundleArtifact> findByName(String name) {
        return byCoordinate.values().stream()
                .filter(a -> a.descriptor().name().equals(name))
                .toList();
    }

    @Override
    public Optional<BundleArtifact> find(String name, String version) {
        return Optional.ofNullable(byCoordinate.get(name + "@" + version));
    }

    @Override
    public boolean exists(String name, String version) {
        return byCoordinate.containsKey(name + "@" + version);
    }

    private static String key(BundleArtifact artifact) {
        return artifact.descriptor().coordinate();
    }
}
