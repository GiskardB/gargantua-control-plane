package ai.gargantua.controlplane.registry;

import ai.gargantua.controlplane.registry.model.BundleArtifact;
import ai.gargantua.controlplane.registry.model.PromotionRequest;
import ai.gargantua.controlplane.registry.model.PublishRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP surface of the Registry: publish bundles, browse versions, fetch the raw
 * manifest, and promote a version between environments.
 */
@RestController
@RequestMapping("/api/v1/registry")
public class RegistryController {

    private final RegistryService registryService;

    public RegistryController(RegistryService registryService) {
        this.registryService = registryService;
    }

    @PostMapping("/bundles")
    @ResponseStatus(HttpStatus.CREATED)
    public BundleArtifact publish(@Valid @RequestBody PublishRequest request) {
        return registryService.publish(request.manifest(), request.skillFiles());
    }

    @GetMapping("/bundles")
    public List<BundleArtifact> list() {
        return registryService.list();
    }

    @GetMapping("/bundles/{name}/versions")
    public List<BundleArtifact> versions(@PathVariable String name) {
        return registryService.versions(name);
    }

    @GetMapping("/bundles/{name}/{version}")
    public BundleArtifact get(@PathVariable String name, @PathVariable String version) {
        return registryService.get(name, version);
    }

    @GetMapping(value = "/bundles/{name}/{version}/manifest", produces = "application/yaml")
    public String manifest(@PathVariable String name, @PathVariable String version) {
        return registryService.manifest(name, version);
    }

    @GetMapping(value = "/bundles/{name}/{version}/bundle", produces = "application/octet-stream")
    public byte[] bundle(@PathVariable String name, @PathVariable String version) {
        return registryService.bundleZip(name, version);
    }

    @PostMapping("/bundles/{name}/{version}/promote")
    public BundleArtifact promote(
            @PathVariable String name,
            @PathVariable String version,
            @Valid @RequestBody PromotionRequest request) {
        return registryService.promote(name, version, request.environment());
    }
}
