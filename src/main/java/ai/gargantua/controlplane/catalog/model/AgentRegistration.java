package ai.gargantua.controlplane.catalog.model;

import java.time.Instant;
import java.util.List;

/**
 * A running agent that has announced itself for Agent-to-Agent (A2A) discovery:
 * where it can be reached and which capabilities it serves. This is the live,
 * runtime-facing counterpart to the static capability index derived from bundles.
 *
 * @param name         unique agent name
 * @param endpoint     URL the agent is reachable at
 * @param capabilities capability names the agent serves
 * @param registeredAt when the registration was accepted
 */
public record AgentRegistration(
        String name,
        String endpoint,
        List<String> capabilities,
        Instant registeredAt) {

    public AgentRegistration {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }
}
