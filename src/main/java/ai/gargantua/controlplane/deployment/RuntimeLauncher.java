package ai.gargantua.controlplane.deployment;

import ai.gargantua.controlplane.common.NotFoundException;
import ai.gargantua.controlplane.registry.RegistryService;
import java.time.Clock;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Starts a Runtime container for a deployment and updates its state.
 * Takes RegistryService to validate the bundle exists; deployment state is
 * updated by the caller (not the launcher itself) to avoid circular deps.
 */
@Service
public class RuntimeLauncher {

    private static final Logger log = LoggerFactory.getLogger(RuntimeLauncher.class);
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]+$");
    private static final long TIMEOUT_MINUTES = 5;

    private final RegistryService registryService;
    private final Clock clock;

    @Value("${gargantua.launch.command-template}")
    private String commandTemplate;

    @Value("${gargantua.launch.workdir}")
    private String workdir;

    public RuntimeLauncher(
            RegistryService registryService,
            Clock clock) {
        this.registryService = registryService;
        this.clock = clock;
    }

    /**
     * Starts the runtime container for a deployment.
     * Caller is responsible for:
     *  1. Validating deployment exists
     *  2. Setting state to PROGRESSING before calling
     *  3. Updating state to HEALTHY or FAILED after based on exit code
     */
    public void launch(String deploymentId, String bundleName, String bundleVersion) {
        // Validate bundle exists
        registryService.get(bundleName, bundleVersion);

        String command = commandTemplate
                .replace("{name}", bundleName)
                .replace("{version}", bundleVersion);

        log.info("Launching runtime for deployment {}: {}", deploymentId, command);

        try {
            Process process = new ProcessBuilder("sh", "-c", command)
                    .directory(new java.io.File(workdir))
                    .redirectErrorStream(true)
                    .start();

            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            boolean finished = process.waitFor(TIMEOUT_MINUTES, java.util.concurrent.TimeUnit.MINUTES);

            if (!finished) {
                log.error("Launch timed out for deployment {}: {}", deploymentId, command);
            } else if (process.exitValue() == 0) {
                log.info("Runtime launched successfully for deployment {}", deploymentId);
            } else {
                log.error("Launch failed for deployment {} (exit {}): {}", deploymentId, process.exitValue(), output);
            }
        } catch (Exception e) {
            log.error("Exception launching runtime for deployment {}", deploymentId, e);
        }
    }
}