package ai.gargantua.controlplane.catalog;

import ai.gargantua.controlplane.catalog.model.AgentRegistration;
import ai.gargantua.controlplane.catalog.model.CapabilityEntry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link Catalog}: two concurrent maps (capability name → entry, agent
 * name → registration), registrations upserted. No longer a Spring bean —
 * {@link JdbcCatalog} is the durable default. Kept as a test double for unit tests.
 */
public class InMemoryCatalog implements Catalog {

    private final Map<String, Capability> capabilities = new ConcurrentHashMap<>();
    private final Map<String, AgentRegistration> agents = new ConcurrentHashMap<>();

    /** Mutable internal accumulator; exposed only as an immutable CapabilityEntry. */
    private static final class Capability {
        String description;
        final Set<String> providers = new LinkedHashSet<>();
    }

    @Override
    public void indexCapability(String name, String description, String providerCoordinate) {
        capabilities.compute(name, (key, existing) -> {
            Capability cap = existing == null ? new Capability() : existing;
            if (description != null && !description.isBlank()) {
                cap.description = description;
            }
            if (providerCoordinate != null) {
                cap.providers.add(providerCoordinate);
            }
            return cap;
        });
    }

    @Override
    public void deindexProvider(String providerCoordinate) {
        capabilities.entrySet().removeIf(entry -> {
            Capability cap = entry.getValue();
            cap.providers.remove(providerCoordinate);
            return cap.providers.isEmpty();
        });
    }

    @Override
    public List<CapabilityEntry> listCapabilities() {
        List<CapabilityEntry> out = new ArrayList<>();
        capabilities.forEach((name, cap) -> out.add(toEntry(name, cap)));
        out.sort((a, b) -> a.name().compareTo(b.name()));
        return out;
    }

    @Override
    public Optional<CapabilityEntry> findCapability(String name) {
        return Optional.ofNullable(capabilities.get(name)).map(cap -> toEntry(name, cap));
    }

    @Override
    public void registerAgent(AgentRegistration registration) {
        agents.put(registration.name(), registration);
    }

    @Override
    public List<AgentRegistration> listAgents() {
        return List.copyOf(agents.values());
    }

    @Override
    public List<AgentRegistration> findAgentsByCapability(String capabilityName) {
        return agents.values().stream()
                .filter(a -> a.capabilities().contains(capabilityName))
                .toList();
    }

    private static CapabilityEntry toEntry(String name, Capability cap) {
        return new CapabilityEntry(name, cap.description, List.copyOf(cap.providers));
    }
}
