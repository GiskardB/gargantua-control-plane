package ai.gargantua.controlplane.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.gargantua.controlplane.common.ConflictException;
import ai.gargantua.controlplane.policy.model.Policy;
import ai.gargantua.controlplane.policy.model.PolicyDecision;
import ai.gargantua.controlplane.policy.model.PolicyEffect;
import ai.gargantua.controlplane.policy.model.PolicyRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PolicyServiceTest {

    private PolicyService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new PolicyService(new InMemoryPolicyManager(), clock);
    }

    private PolicyRequest req(String name, String target, PolicyEffect effect) {
        return new PolicyRequest(name, "desc", target, effect);
    }

    @Test
    void noMatchingPolicyIsAllow() {
        PolicyDecision d = service.evaluate("capability:send-email");
        assertThat(d.decision()).isEqualTo(PolicyEffect.ALLOW);
        assertThat(d.matched()).isEmpty();
    }

    @Test
    void exactDenyMatches() {
        service.create(req("no-email", "capability:send-email", PolicyEffect.DENY));
        PolicyDecision d = service.evaluate("capability:send-email");
        assertThat(d.decision()).isEqualTo(PolicyEffect.DENY);
        assertThat(d.matched()).singleElement()
                .extracting(Policy::name).isEqualTo("no-email");
    }

    @Test
    void wildcardTargetMatchesByPrefix() {
        service.create(req("no-caps", "capability:*", PolicyEffect.DENY));
        assertThat(service.evaluate("capability:send-email").decision())
                .isEqualTo(PolicyEffect.DENY);
        assertThat(service.evaluate("tool:shell").decision())
                .isEqualTo(PolicyEffect.ALLOW);
    }

    @Test
    void denyOverridesAllowWhenBothMatch() {
        service.create(req("allow-email", "capability:send-email", PolicyEffect.ALLOW));
        service.create(req("deny-email", "capability:send-email", PolicyEffect.DENY));
        PolicyDecision d = service.evaluate("capability:send-email");
        assertThat(d.decision()).isEqualTo(PolicyEffect.DENY);
        assertThat(d.matched()).hasSize(2);
    }

    @Test
    void updateChangesEffectAndPreservesId() {
        Policy created = service.create(req("p", "capability:x", PolicyEffect.ALLOW));
        Policy updated = service.update(created.id(), req("p", "capability:x", PolicyEffect.DENY));
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
        assertThat(updated.effect()).isEqualTo(PolicyEffect.DENY);
    }

    @Test
    void renamingOntoAnotherPolicyNameConflicts() {
        service.create(req("a", "capability:a", PolicyEffect.ALLOW));
        Policy b = service.create(req("b", "capability:b", PolicyEffect.ALLOW));
        assertThatThrownBy(() -> service.update(b.id(), req("a", "capability:b", PolicyEffect.ALLOW)))
                .isInstanceOf(ConflictException.class);
    }
}
