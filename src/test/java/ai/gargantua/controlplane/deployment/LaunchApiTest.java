package ai.gargantua.controlplane.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The launch endpoint through the real context, where {@code @Async} is live. The test
 * profile's command template is {@code true}, so "launching" succeeds without Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LaunchApiTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private String publishAndDeploy(String name) throws Exception {
        String manifest = """
                apiVersion: gargantua.ai/v1
                kind: Agent
                metadata:
                  name: "%s"
                  version: 1.0.0
                spec: {}
                """.formatted(name);
        mvc.perform(post("/api/v1/registry/bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("manifest", manifest))))
                .andExpect(status().isCreated());
        String created = mvc.perform(post("/api/v1/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "bundleName", name, "bundleVersion", "1.0.0", "environment", "DEV"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(created).path("id").asText();
    }

    @Test
    void launchAnswersProgressingThenSettlesHealthyInTheBackground() throws Exception {
        String id = publishAndDeploy("launch-ok");

        mvc.perform(post("/api/v1/deployments/" + id + "/launch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PROGRESSING"));

        String state = "PROGRESSING";
        for (int i = 0; i < 100 && "PROGRESSING".equals(state); i++) {
            Thread.sleep(50);
            state = json.readTree(mvc.perform(get("/api/v1/deployments/" + id))
                    .andReturn().getResponse().getContentAsString()).path("state").asText();
        }
        assertThat(state).isEqualTo("HEALTHY");
    }

    @Test
    void launchingABundleWhoseNameIsNotShellSafeIs400AndLeavesItPending() throws Exception {
        String id = publishAndDeploy("evil; touch pwned");

        mvc.perform(post("/api/v1/deployments/" + id + "/launch"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/deployments/" + id))
                .andExpect(jsonPath("$.state").value("PENDING"));
    }
}
