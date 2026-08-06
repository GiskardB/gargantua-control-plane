package ai.gargantua.controlplane.policy;

import ai.gargantua.controlplane.common.ConflictException;
import ai.gargantua.controlplane.common.NotFoundException;
import ai.gargantua.controlplane.policy.model.Policy;
import ai.gargantua.controlplane.policy.model.PolicyDecision;
import ai.gargantua.controlplane.policy.model.PolicyEffect;
import ai.gargantua.controlplane.policy.model.PolicyRequest;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The Policy subsystem's use cases: CRUD over governance policies plus a decision
 * endpoint. The Control Plane <em>defines</em> policy and states decisions; the
 * Runtime and Gateway <em>enforce</em> them (ADR-004). Richer policy types (rate
 * limits, RBAC) remain future work.
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

    /** Replace a policy's mutable fields, preserving its id and creation time. */
    public Policy update(String id, PolicyRequest request) {
        Policy existing = get(id);
        // A rename may not collide with a different policy.
        if (!existing.name().equals(request.name()) && policyManager.existsByName(request.name())) {
            throw new ConflictException("policy '" + request.name() + "' already exists");
        }
        Policy updated = new Policy(
                existing.id(),
                request.name(),
                request.description(),
                request.target(),
                request.effect(),
                existing.createdAt());
        policyManager.save(updated);
        return updated;
    }

    public void delete(String id) {
        if (!policyManager.delete(id)) {
            throw new NotFoundException("policy '" + id + "' not found");
        }
    }

    /**
     * Decide a target against the stored policies. A policy applies on an exact
     * target match, or by prefix when its target ends in {@code *}. Deny overrides;
     * no matching policy means ALLOW. See {@link PolicyDecision}.
     */
    public PolicyDecision evaluate(String target) {
        List<Policy> matched = policyManager.findAll().stream()
                .filter(p -> targetMatches(p.target(), target))
                .toList();
        PolicyEffect decision = matched.stream().anyMatch(p -> p.effect() == PolicyEffect.DENY)
                ? PolicyEffect.DENY
                : PolicyEffect.ALLOW;
        return new PolicyDecision(target, decision, matched);
    }

    private static boolean targetMatches(String policyTarget, String requested) {
        if (policyTarget.endsWith("*")) {
            return requested.startsWith(policyTarget.substring(0, policyTarget.length() - 1));
        }
        return policyTarget.equals(requested);
    }
}
