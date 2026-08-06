package ai.gargantua.controlplane.policy;

import ai.gargantua.controlplane.common.ConflictException;
import ai.gargantua.controlplane.common.NotFoundException;
import ai.gargantua.controlplane.policy.model.Policy;
import ai.gargantua.controlplane.policy.model.PolicyRequest;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The Policy subsystem's use cases: create, list, fetch and delete governance
 * policies. A deliberately thin CRUD stub for the MVP — the domain model reserves
 * richer policy types (rate limits, RBAC) for later, and enforcement lives elsewhere.
 */
@Service
public class PolicyService {

    private final PolicyManager policyManager;
    private final Clock clock;

    public PolicyService(PolicyManager policyManager, Clock clock) {
        this.policyManager = policyManager;
        this.clock = clock;
    }

    public Policy create(PolicyRequest request) {
        if (policyManager.existsByName(request.name())) {
            throw new ConflictException("policy '" + request.name() + "' already exists");
        }
        Policy policy = new Policy(
                UUID.randomUUID().toString(),
                request.name(),
                request.description(),
                request.target(),
                request.effect(),
                clock.instant());
        policyManager.save(policy);
        return policy;
    }

    public List<Policy> list() {
        return policyManager.findAll();
    }

    public Policy get(String id) {
        return policyManager.find(id)
                .orElseThrow(() -> new NotFoundException("policy '" + id + "' not found"));
    }

    public void delete(String id) {
        if (!policyManager.delete(id)) {
            throw new NotFoundException("policy '" + id + "' not found");
        }
    }
}
