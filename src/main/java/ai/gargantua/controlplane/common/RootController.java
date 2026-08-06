package ai.gargantua.controlplane.common;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** A self-describing landing endpoint listing the Control Plane's subsystems. */
@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, Object> index() {
        return Map.of(
                "service", "gargantua-control-plane",
                "apiVersion", "gargantua.ai/v1",
                "subsystems", Map.of(
                        "registry", "/api/v1/registry",
                        "catalog", "/api/v1/catalog",
                        "policies", "/api/v1/policies",
                        "deployments", "/api/v1/deployments"),
                "health", "/actuator/health");
    }
}
