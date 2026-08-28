package ai.gargantua.controlplane;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Gargantua Control Plane.
 *
 * <p>Owns the platform's desired state — the artifacts, the discovery index, the
 * policies and the deployments — and exposes it over an API. The Runtime applies
 * this state; it never becomes the source of truth for it (ADR-004).
 *
 * <p>Four subsystems, each behind a port so the MVP's in-memory backends
 * can be swapped for Postgres without touching callers:
 * Registry, Catalog, Policy Manager, Deployment Manager.
 */
@SpringBootApplication
@EnableAsync
public class ControlPlaneApplication {
    public static void main(String[] args) {
        SpringApplication.run(ControlPlaneApplication.class, args);
    }
}
