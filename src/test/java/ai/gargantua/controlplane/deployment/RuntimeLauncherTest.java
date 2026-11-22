package ai.gargantua.controlplane.deployment;

import ai.gargantua.controlplane.common.ValidationException;
import ai.gargantua.controlplane.deployment.model.DeploymentState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RuntimeLauncherTest {

    @TempDir
    Path workdir;

    private final DeploymentService deployments = mock(DeploymentService.class);

    private RuntimeLauncher launcher(String template, long timeoutSeconds) {
        return new RuntimeLauncher(deployments, template, workdir.toString(), timeoutSeconds);
    }

    @ParameterizedTest
    @ValueSource(strings = {"x; touch pwned", "$(touch pwned)", "`touch pwned`", "a b", "a|b", "a&&b", "../etc", ""})
    void aNameThatIsNotShellSafeIsRefusedAndNothingRuns(String name) {
        var launcher = launcher("echo {name} > ran; touch pwned-marker", 5);

        assertThatThrownBy(() -> launcher.launch("d1", name, "1.0.0"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> launcher.launch("d1", "agent", name))
                .isInstanceOf(ValidationException.class);

        assertThat(workdir).isEmptyDirectory();
    }

    @Test
    void nameAndVersionAreSubstitutedIntoTheCommand() throws Exception {
        var result = launcher("echo {name}@{version} > out.txt", 5).launch("d1", "hello-agent", "1.2.0");

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(workdir.resolve("out.txt")).trim()).isEqualTo("hello-agent@1.2.0");
    }

    @Test
    void aNonZeroExitIsAFailureCarryingTheCommandOutput() {
        var result = launcher("echo boom; exit 3", 5).launch("d1", "agent", "1.0.0");

        assertThat(result.success()).isFalse();
        assertThat(result.detail()).contains("exit 3").contains("boom");
    }

    @Test
    void aCommandThatOutlivesTheTimeoutIsKilledAndReportedFailed() {
        long started = System.nanoTime();

        var result = launcher("sleep 30", 1).launch("d1", "agent", "1.0.0");

        assertThat(result.success()).isFalse();
        assertThat(result.detail()).contains("timed out");
        assertThat((System.nanoTime() - started) / 1_000_000_000L).isLessThan(10);
    }

    @Test
    void backgroundLaunchRecordsHealthyOnSuccess() {
        launcher("true", 5).launchAsync("d1", "agent", "1.0.0");

        verify(deployments).updateState("d1", DeploymentState.HEALTHY);
    }

    @Test
    void backgroundLaunchRecordsFailedWhenTheCommandFails() {
        launcher("exit 1", 5).launchAsync("d1", "agent", "1.0.0");

        verify(deployments).updateState("d1", DeploymentState.FAILED);
    }
}
