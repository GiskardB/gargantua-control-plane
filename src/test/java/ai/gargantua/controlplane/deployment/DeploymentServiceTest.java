package ai.gargantua.controlplane.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ai.gargantua.controlplane.common.NotFoundException;
import ai.gargantua.controlplane.deployment.model.Deployment;
import ai.gargantua.controlplane.deployment.model.DeploymentRequest;
import ai.gargantua.controlplane.deployment.model.DeploymentState;
import ai.gargantua.controlplane.registry.RegistryService;
import ai.gargantua.controlplane.registry.model.Environment;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeploymentServiceTest {

    private DeploymentService service;

    @BeforeEach
    void setUp() {
        RegistryService registryService = mock(RegistryService.class); // create() only needs it not to throw
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new DeploymentService(new InMemoryDeploymentManager(), registryService, clock);
    }

    @Test
    void undeployRemovesTheRecordSoItNoLongerBlocksRegistryDeletes() {
        Deployment created = service.create(new DeploymentRequest("agent", "1.0.0", Environment.DEV, null));

        service.undeploy(created.id());

        assertThatThrownBy(() -> service.get(created.id())).isInstanceOf(NotFoundException.class);
        assertThat(service.list()).isEmpty();
    }

    @Test
    void undeployingAnUnknownIdIs404() {
        assertThatThrownBy(() -> service.undeploy("missing")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateStateRecordsThePortOnceKnown() {
        Deployment created = service.create(new DeploymentRequest("agent", "1.0.0", Environment.DEV, null));

        Deployment healthy = service.updateState(created.id(), DeploymentState.HEALTHY, 18104);

        assertThat(healthy.port()).isEqualTo(18104);
    }

    @Test
    void updateStateWithoutAPortKeepsWhateverWasRecordedBefore() {
        Deployment created = service.create(new DeploymentRequest("agent", "1.0.0", Environment.DEV, null));
        service.updateState(created.id(), DeploymentState.HEALTHY, 18104);

        // A later state change (e.g. a health-check failure) that doesn't know the port
        // must not blank out the one already on record.
        Deployment failed = service.updateState(created.id(), DeploymentState.FAILED);

        assertThat(failed.port()).isEqualTo(18104);
    }
}
