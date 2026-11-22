package ai.gargantua.controlplane.deployment;

import ai.gargantua.controlplane.deployment.model.Deployment;
import ai.gargantua.controlplane.deployment.model.DeploymentState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Opt-in endpoint that makes the Control Plane itself start a Runtime.
 *
 * <p>Off by default. ADR-004 has the Control Plane <em>define</em> desired state and an
 * executor apply it: in the reference stack that executor is Studio, which runs the
 * launch command and reports the outcome back through {@code PUT /{id}/state}. A Control
 * Plane that shells out is also a Control Plane that needs a container runtime and the
 * privileges to drive it, which is not something to grant by default. Set
 * {@code gargantua.launch.enabled=true} for a deployment where nothing else can launch.
 */
@RestController
@RequestMapping("/api/v1/deployments")
@ConditionalOnProperty(prefix = "gargantua.launch", name = "enabled", havingValue = "true")
public class DeploymentLaunchController {

    private final DeploymentService deploymentService;
    private final RuntimeLauncher runtimeLauncher;

    public DeploymentLaunchController(DeploymentService deploymentService, RuntimeLauncher runtimeLauncher) {
        this.deploymentService = deploymentService;
        this.runtimeLauncher = runtimeLauncher;
    }

    @PostMapping("/{id}/launch")
    public Deployment launch(@PathVariable String id) {
        Deployment deployment = deploymentService.get(id);
        // Refuse an unlaunchable name here, synchronously, rather than in the background.
        runtimeLauncher.validate(deployment.bundleName(), deployment.bundleVersion());
        Deployment progressing = deploymentService.updateState(id, DeploymentState.PROGRESSING);
        runtimeLauncher.launchAsync(deployment.id(), deployment.bundleName(), deployment.bundleVersion());
        return progressing;
    }
}
