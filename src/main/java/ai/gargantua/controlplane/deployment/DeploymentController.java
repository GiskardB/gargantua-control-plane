package ai.gargantua.controlplane.deployment;

import ai.gargantua.controlplane.deployment.model.Deployment;
import ai.gargantua.controlplane.deployment.model.DeploymentRequest;
import ai.gargantua.controlplane.deployment.model.DeploymentStateRequest;
import ai.gargantua.controlplane.registry.model.Environment;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP surface of the Deployment Manager: create deployments, list and fetch them,
 * launch runtimes, and record observed state transitions.
 */
@RestController
@RequestMapping("/api/v1/deployments")
public class DeploymentController {

    private final DeploymentService deploymentService;

    public DeploymentController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Deployment create(@Valid @RequestBody DeploymentRequest request) {
        return deploymentService.create(request);
    }

    @GetMapping
    public List<Deployment> list(
            @RequestParam(name = "environment", required = false) Environment environment,
            @RequestParam(name = "bundle", required = false) String bundle) {
        return environment == null && bundle == null
                ? deploymentService.list()
                : deploymentService.list(environment, bundle);
    }

    @GetMapping("/{id}")
    public Deployment get(@PathVariable String id) {
        return deploymentService.get(id);
    }

    /**
     * Triggers the Control Plane to start the runtime container for this deployment.
     * Returns immediately after marking state PROGRESSING — the actual launch happens
     * in a background thread. Poll GET /api/v1/deployments/{id} to see final state.
     */
    @PutMapping("/{id}/state")
    public Deployment updateState(
            @PathVariable String id,
            @Valid @RequestBody DeploymentStateRequest request) {
        return deploymentService.updateState(id, request.state(), request.port());
    }

    /** Clears this deployment record so its bundle version is no longer "still deployed". */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undeploy(@PathVariable String id) {
        deploymentService.undeploy(id);
    }
}