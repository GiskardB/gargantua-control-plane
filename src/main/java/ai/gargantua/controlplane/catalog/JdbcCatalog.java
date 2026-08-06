package ai.gargantua.controlplane.catalog;

import ai.gargantua.controlplane.catalog.model.AgentRegistration;
import ai.gargantua.controlplane.catalog.model.CapabilityEntry;
import ai.gargantua.controlplane.storage.DocumentStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * Durable {@link Catalog} over the {@link DocumentStore}. Two collections: the
 * capability index (name → entry) and the A2A agent registrations (name →
 * registration). Indexing a capability is a read-merge-write upsert so a new
 * provider is added to the existing entry's provider set without losing the others.
 */
@Repository
public class JdbcCatalog implements Catalog {

    private static final String CAPS = "catalog_capabilities";
    private static final String AGENTS = "catalog_agents";

    private final DocumentStore store;

    public JdbcCatalog(DocumentStore store) {
        this.store = store;
    }

    @Override
    public void indexCapability(String name, String description, String providerCoordinate) {
        CapabilityEntry existing = store.get(CAPS, name, CapabilityEntry.class).orElse(null);

        Set<String> providers = new LinkedHashSet<>();
        String desc = description;
        if (existing != null) {
            providers.addAll(existing.providers());
            if (desc == null || desc.isBlank()) {
                desc = existing.description();
            }
        }
        if (providerCoordinate != null) {
            providers.add(providerCoordinate);
        }
        store.put(CAPS, name, new CapabilityEntry(name, desc, List.copyOf(providers)));
    }

    @Override
    public List<CapabilityEntry> listCapabilities() {
        List<CapabilityEntry> all = store.list(CAPS, CapabilityEntry.class);
        all.sort(Comparator.comparing(CapabilityEntry::name));
        return all;
    }

    @Override
    public Optional<CapabilityEntry> findCapability(String name) {
        return store.get(CAPS, name, CapabilityEntry.class);
    }

    @Override
    public void registerAgent(AgentRegistration registration) {
        store.put(AGENTS, registration.name(), registration);
    }

    @Override
    public List<AgentRegistration> listAgents() {
        return store.list(AGENTS, AgentRegistration.class);
    }

    @Override
    public List<AgentRegistration> findAgentsByCapability(String capabilityName) {
        List<AgentRegistration> matches = new ArrayList<>();
        for (AgentRegistration agent : store.list(AGENTS, AgentRegistration.class)) {
            if (agent.capabilities().contains(capabilityName)) {
                matches.add(agent);
            }
        }
        return matches;
    }
}
