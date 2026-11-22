package ai.gargantua.controlplane.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

/** Out of the box the Control Plane records deployments; it does not execute them (ADR-004). */
@SpringBootTest
@AutoConfigureMockMvc
class LaunchDisabledByDefaultTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void theLaunchEndpointDoesNotExistAndNoLauncherIsWired() throws Exception {
        mvc.perform(post("/api/v1/deployments/any-id/launch")).andExpect(status().isNotFound());

        assertThat(context.getBeansOfType(RuntimeLauncher.class)).isEmpty();
    }
}
