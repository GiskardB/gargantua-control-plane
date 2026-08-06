package ai.gargantua.controlplane.domain;

import java.util.List;

/**
 * A named unit of work a workload declares it can perform. Mirrors the Capability
 * object in the Gargantua domain model: it is the currency the Catalog indexes so
 * that agents can discover each other by what they do, not by where they live.
 *
 * <p>Kept as a local record rather than an agent-core dependency (agent-core targets
 * Java 21; this service runs on Java 17). The contract is the domain model doc.
 *
 * @param name         stable identifier, e.g. {@code summarize-document}
 * @param description  human-readable statement of what it does
 * @param version      semantic version of the capability contract
 * @param inputSchema  JSON Schema (as a string) describing the input, may be null
 * @param outputSchema JSON Schema (as a string) describing the output, may be null
 * @param tags         free-form labels for discovery/filtering
 */
public record Capability(
        String name,
        String description,
        String version,
        String inputSchema,
        String outputSchema,
        List<String> tags) {

    public Capability {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("capability name must not be blank");
        }
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
