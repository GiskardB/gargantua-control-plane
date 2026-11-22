package ai.gargantua.controlplane.deployment;

import ai.gargantua.controlplane.common.ValidationException;
import ai.gargantua.controlplane.deployment.model.DeploymentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Runs the configured command that starts the Runtime for a deployment and records the
 * outcome on the deployment.
 *
 * <p>The bundle name and version are substituted into a string handed to {@code sh -c},
 * and both originate in a manifest anyone with publish access can write. They are
 * therefore checked against {@link #SAFE} before anything is substituted: a name such as
 * {@code x; rm -rf /} is refused, never executed.
 */
@Service
@ConditionalOnProperty(prefix = "gargantua.launch", name = "enabled", havingValue = "true")
public class RuntimeLauncher {

    private static final Logger log = LoggerFactory.getLogger(RuntimeLauncher.class);

    /** The only characters a name or version may contain to be placed in a shell command. */
    static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]+$");

    private static final int MAX_OUTPUT_CHARS = 8000;

    private final DeploymentService deploymentService;
    private final String commandTemplate;
    private final File workdir;
    private final long timeoutSeconds;

    public RuntimeLauncher(
            DeploymentService deploymentService,
            @Value("${gargantua.launch.command-template}") String commandTemplate,
            @Value("${gargantua.launch.workdir:.}") String workdir,
            @Value("${gargantua.launch.timeout-seconds:300}") long timeoutSeconds) {
        this.deploymentService = deploymentService;
        this.commandTemplate = commandTemplate;
        this.workdir = new File(workdir);
        this.timeoutSeconds = timeoutSeconds;
    }

    /** Outcome of running the launch command. */
    public record LaunchResult(boolean success, String detail) {
    }

    /**
     * Rejects a name or version that is not safe to place in a shell command. Called on
     * the request thread, before the launch goes asynchronous, so the caller gets a 400
     * instead of a deployment that silently turns {@code FAILED}.
     */
    public void validate(String bundleName, String bundleVersion) {
        if (bundleName == null || !SAFE.matcher(bundleName).matches()) {
            throw new ValidationException("bundle name '" + bundleName
                    + "' cannot be launched: only letters, digits, '.', '_' and '-' are allowed");
        }
        if (bundleVersion == null || !SAFE.matcher(bundleVersion).matches()) {
            throw new ValidationException("bundle version '" + bundleVersion
                    + "' cannot be launched: only letters, digits, '.', '_' and '-' are allowed");
        }
    }

    /**
     * Launches in the background and moves the deployment to {@code HEALTHY} or
     * {@code FAILED}. Lives on this bean rather than on the controller because
     * {@code @Async} only takes effect through the Spring proxy, i.e. when called from
     * another bean.
     */
    @Async
    public void launchAsync(String deploymentId, String bundleName, String bundleVersion) {
        DeploymentState outcome;
        try {
            outcome = launch(deploymentId, bundleName, bundleVersion).success()
                    ? DeploymentState.HEALTHY : DeploymentState.FAILED;
        } catch (RuntimeException e) {
            log.error("Launch of deployment {} could not be started", deploymentId, e);
            outcome = DeploymentState.FAILED;
        }
        try {
            deploymentService.updateState(deploymentId, outcome);
        } catch (RuntimeException e) {
            // Undeployed while the command was running: nothing left to update.
            log.warn("Deployment {} finished as {} but its record is gone", deploymentId, outcome);
        }
    }

    /** Runs the command and waits for it, up to the configured timeout. */
    public LaunchResult launch(String deploymentId, String bundleName, String bundleVersion) {
        validate(bundleName, bundleVersion);
        String command = commandTemplate
                .replace("{name}", bundleName)
                .replace("{version}", bundleVersion);
        log.info("Launching runtime for deployment {}: {}", deploymentId, command);

        Path output = null;
        try {
            // Output goes to a file rather than a pipe: reading a pipe to EOF blocks for
            // as long as any child keeps it open, which made the timeout below unreachable.
            output = Files.createTempFile("gargantua-launch-", ".log");
            Process process = new ProcessBuilder("sh", "-c", command)
                    .directory(workdir)
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.error("Launch timed out after {}s for deployment {}", timeoutSeconds, deploymentId);
                return new LaunchResult(false, "timed out after " + timeoutSeconds + "s");
            }
            String tail = tail(output);
            if (process.exitValue() == 0) {
                log.info("Runtime launched for deployment {}", deploymentId);
                return new LaunchResult(true, tail);
            }
            log.error("Launch failed for deployment {} (exit {}): {}", deploymentId, process.exitValue(), tail);
            return new LaunchResult(false, "exit " + process.exitValue() + ": " + tail);
        } catch (IOException e) {
            log.error("Could not start launch command for deployment {}", deploymentId, e);
            return new LaunchResult(false, "could not start command: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new LaunchResult(false, "interrupted");
        } finally {
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException ignored) {
                    // A leftover temp file is harmless.
                }
            }
        }
    }

    private static String tail(Path output) throws IOException {
        String text = Files.readString(output, StandardCharsets.UTF_8);
        return text.length() <= MAX_OUTPUT_CHARS ? text : text.substring(text.length() - MAX_OUTPUT_CHARS);
    }
}
