package ai.gargantua.controlplane.policy;

import ai.gargantua.controlplane.policy.model.Policy;
import ai.gargantua.controlplane.policy.model.PolicyDecision;
import ai.gargantua.controlplane.policy.model.PolicyEvaluationRequest;
import ai.gargantua.controlplane.policy.model.PolicyRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP surface of the Policy Manager: CRUD over governance policies. */
@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Policy create(@Valid @RequestBody PolicyRequest request) {
        return policyService.create(request);
    }

    @GetMapping
    public List<Policy> list() {
        return policyService.list();
    }

    @GetMapping("/{id}")
    public Policy get(@PathVariable String id) {
        return policyService.get(id);
    }

    @PutMapping("/{id}")
    public Policy update(@PathVariable String id, @Valid @RequestBody PolicyRequest request) {
        return policyService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        policyService.delete(id);
    }

    /** Decide a target against the stored policies (deny overrides; no match = allow). */
    @PostMapping("/evaluate")
    public PolicyDecision evaluate(@Valid @RequestBody PolicyEvaluationRequest request) {
        return policyService.evaluate(request.target());
    }
}
