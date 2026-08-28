package ai.gargantua.controlplane.deployment;

import ai.gargantua.controlplane.common.NotFoundException;
import ai.gargantua.controlplane.deployment.model.Deployment;
import ai.gargantua.controlplane.deployment.model.DeploymentRequest;
import ai.gargantua.controlplane.deployment.model.DeploymentState;
import ai.gargantua.controlplane.registry.RegistryService;
import ai.gargantua.controlplane.registry.model.Environment;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The Deployment subsystem's use cases: record a desired deployment and expose its
 * last-known state. Validates bundle existence before accepting deployment.
 * Launch is triggered via POST /{id}/launch endpoint.
 */
@Service
public class DeploymentService {

    private final DeploymentManager deploymentManager;
    private final RegistryService registryService;
    private final Clock clock;

    public DeploymentService(
            DeploymentManager deploymentManager,
            RegistryService registryService,
            Clock clock) {
        this.deploymentManager = deploymentManager;
        this.registryService = registryService;
        this.clock = clock;
    }

    public Deployment create(DeploymentRequest request) {
        // 404 if the referenced bundle version was never published.
        registryService.get(request.bundleName(), request.bundleVersion());

        Deployment deployment = new Deployment(
                UUID.randomUUID().toString(),
                request.bundleName(),
                request.bundleVersion(),
                request.environment(),
                request.strategyOrDefault(),
                DeploymentState.PENDING,
                clock.instant(),
                clock.instant());
        deploymentManager.save(deployment);
        return deployment;
    }

    public List<Deployment> list() {
        return deploymentManager.findAll();
    }

    /**
     * List deployments, optionally narrowed by environment and/or bundle name.
     * Null filters are ignored — useful for "what is deployed to PRODUCTION" or
     * "where is bundle X running".
     */
    public List<Deployment> list(Environment environment, String bundleName) {
        return deploymentManager.findAll().stream()
                .filter(d -> environment == null || d.environment() == environment)
                .filter(d -> bundleName == null || d.bundleName().equals(bundleName))
                .toList();
    }

    public Deployment get(String id) {
        return deploymentManager.find(id)
                .orElseThrow(() -> new NotFoundException("deployment '" + id + "' not found"));
    }

    /** Record a state transition reported for a deployment. */
    public Deployment updateState(String id, DeploymentState state) {
        Deployment current = get(id);
        Deployment next = current.withState(state, clock.instant());
        deploymentManager.save(next);
        return next;
    }
}