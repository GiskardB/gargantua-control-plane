package ai.gargantua.controlplane.deployment;

import ai.gargantua.controlplane.deployment.model.Deployment;
import ai.gargantua.controlplane.storage.DocumentStore;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Durable {@link DeploymentManager} over the {@link DocumentStore}, keyed by id. */
@Repository
public class JdbcDeploymentManager implements DeploymentManager {

    private static final String COLLECTION = "deployments";

    private final DocumentStore store;

    public JdbcDeploymentManager(DocumentStore store) {
        this.store = store;
    }

    @Override
    public void save(Deployment deployment) {
        store.put(COLLECTION, deployment.id(), deployment);
    }

    @Override
    public List<Deployment> findAll() {
        return store.list(COLLECTION, Deployment.class);
    }

    @Override
    public Optional<Deployment> find(String id) {
        return store.get(COLLECTION, id, Deployment.class);
    }
}
