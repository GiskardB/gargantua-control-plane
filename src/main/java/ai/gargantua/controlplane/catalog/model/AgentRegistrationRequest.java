package ai.gargantua.controlplane.catalog.model;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Request an agent sends to announce itself to the Catalog (A2A registration).
 *
 * @param name         unique agent name
 * @param endpoint     URL the agent is reachable at
 * @param capabilities capability names the agent serves
 */
public record AgentRegistrationRequest(
        @NotBlank String name,
        @NotBlank String endpoint,
        List<String> capabilities) {
}
