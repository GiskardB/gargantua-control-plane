package ai.gargantua.controlplane.catalog;

import ai.gargantua.controlplane.catalog.model.AgentRegistration;
import ai.gargantua.controlplane.catalog.model.AgentRegistrationRequest;
import ai.gargantua.controlplane.catalog.model.CapabilityEntry;
import ai.gargantua.controlplane.common.NotFoundException;
import ai.gargantua.controlplane.registry.model.PublishedBundle;
import ai.gargantua.core.capability.Capability;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The Catalog subsystem's use cases: keep the capability index in step with what
 * the Registry publishes, and let running agents register for A2A discovery.
 */
@Service
public class CatalogService {

    private final Catalog catalog;
    private final Clock clock;

    public CatalogService(Catalog catalog, Clock clock) {
        this.catalog = catalog;
        this.clock = clock;
    }

    /**
     * Index every capability a freshly published bundle declares, attributing it to
     * that bundle's coordinate. Called by the Registry on publish so discovery never
     * lags behind the artifact store.
     */
    public void indexBundle(PublishedBundle descriptor) {
        for (Capability capability : descriptor.capabilities()) {
            catalog.indexCapability(
                    capability.name(),
                    capability.description(),
                    descriptor.coordinate());
        }
    }

    /**
     * Remove a bundle's capabilities from the index. Called by the Registry on
     * delete so discovery never advertises a provider that no longer exists.
     */
    public void deindexBundle(String coordinate) {
        catalog.deindexProvider(coordinate);
    }

    public List<CapabilityEntry> capabilities() {
        return catalog.listCapabilities();
    }

    public CapabilityEntry capability(String name) {
        return catalog.findCapability(name)
                .orElseThrow(() -> new NotFoundException("no capability named '" + name + "'"));
    }

    /** Register (or refresh) an agent for A2A discovery. */
    public AgentRegistration register(AgentRegistrationRequest request) {
        AgentRegistration registration = new AgentRegistration(
                request.name(),
                request.endpoint(),
                request.capabilities(),
                clock.instant());
        catalog.registerAgent(registration);
        return registration;
    }

    public List<AgentRegistration> agents() {
        return catalog.listAgents();
    }

    public List<AgentRegistration> agentsByCapability(String capabilityName) {
        return catalog.findAgentsByCapability(capabilityName);
    }
}
