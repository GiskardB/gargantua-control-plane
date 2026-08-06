package ai.gargantua.controlplane;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP tests through the real Spring context: publishing a bundle makes
 * its capability discoverable, and error cases map to the uniform ApiError body.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ControlPlaneApiTest {

    private static final String MANIFEST = """
            apiVersion: gargantua.ai/v1
            kind: Agent
            metadata:
              name: translator
              version: 2.1.0
            spec:
              capabilities:
                - name: translate-text
                  description: Translate text between languages
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private String publishBody() throws Exception {
        return json.writeValueAsString(Map.of("manifest", MANIFEST));
    }

    @Test
    void publishThenDiscoverCapabilityAndManifest() throws Exception {
        mvc.perform(post("/api/v1/registry/bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descriptor.name").value("translator"))
                .andExpect(jsonPath("$.descriptor.kind").value("AGENT"));

        mvc.perform(get("/api/v1/catalog/capabilities/translate-text"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers", hasSize(1)))
                .andExpect(jsonPath("$.providers[0]").value("translator@2.1.0"));

        mvc.perform(get("/api/v1/registry/bundles/translator/2.1.0/manifest"))
                .andExpect(status().isOk());
    }

    @Test
    void malformedManifestReturns400WithApiError() throws Exception {
        String body = json.writeValueAsString(Map.of("manifest", "not: a: valid: manifest"));
        mvc.perform(post("/api/v1/registry/bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/registry/bundles"));
    }

    @Test
    void unknownBundleReturns404() throws Exception {
        mvc.perform(get("/api/v1/registry/bundles/ghost/9.9.9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void agentRegistrationIsDiscoverableByCapability() throws Exception {
        String reg = json.writeValueAsString(Map.of(
                "name", "translator-instance-1",
                "endpoint", "http://translator.local:8080",
                "capabilities", java.util.List.of("translate-text")));
        mvc.perform(post("/api/v1/catalog/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reg))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/catalog/agents").param("capability", "translate-text"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("translator-instance-1"));
    }
}
