package ai.gargantua.controlplane.catalog;

import ai.gargantua.controlplane.catalog.model.AgentRegistration;
import ai.gargantua.controlplane.catalog.model.AgentRegistrationRequest;
import ai.gargantua.controlplane.catalog.model.CapabilityEntry;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP surface of the Catalog: browse capabilities and who provides them, and
 * register/list agents for A2A discovery.
 */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/capabilities")
    public List<CapabilityEntry> capabilities() {
        return catalogService.capabilities();
    }

    @GetMapping("/capabilities/{name}")
    public CapabilityEntry capability(@PathVariable String name) {
        return catalogService.capability(name);
    }

    @PostMapping("/agents")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentRegistration register(@Valid @RequestBody AgentRegistrationRequest request) {
        return catalogService.register(request);
    }

    @GetMapping("/agents")
    public List<AgentRegistration> agents(
            @RequestParam(name = "capability", required = false) String capability) {
        return capability == null
                ? catalogService.agents()
                : catalogService.agentsByCapability(capability);
    }
}
