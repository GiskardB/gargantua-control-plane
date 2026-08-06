package ai.gargantua.controlplane.catalog;

import ai.gargantua.controlplane.catalog.model.AgentRegistration;
import ai.gargantua.controlplane.catalog.model.CapabilityEntry;
import java.util.List;
import java.util.Optional;

/**
 * The discovery index. A port (ADR-004): the MVP is in-memory, but the contract is
 * what a search-backed implementation would honour. Holds two related indices — the
 * static capability→providers map derived from published bundles, and the live A2A
 * agent registrations.
 */
public interface Catalog {

    /** Record that a provider supplies a capability; upserts the entry. */
    void indexCapability(String name, String description, String providerCoordinate);

    List<CapabilityEntry> listCapabilities();

    Optional<CapabilityEntry> findCapability(String name);

    /** Upsert an agent registration, keyed by agent name. */
    void registerAgent(AgentRegistration registration);

    List<AgentRegistration> listAgents();

    List<AgentRegistration> findAgentsByCapability(String capabilityName);
}
