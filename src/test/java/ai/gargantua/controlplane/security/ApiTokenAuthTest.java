package ai.gargantua.controlplane.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "gargantua.security.admin-tokens=admin-secret, second-admin",
        "gargantua.security.reader-tokens=reader-secret"})
@AutoConfigureMockMvc
class ApiTokenAuthTest {

    private static final String PUBLISH = """
            {"manifest":"apiVersion: gargantua.ai/v1\\nkind: Agent\\nmetadata:\\n  name: auth-agent\\n  version: 1.0.0\\nspec: {}\\n"}
            """;

    @Autowired
    private MockMvc mvc;

    @Test
    void withoutATokenTheApiIs401AndSaysHowToAuthenticate() throws Exception {
        mvc.perform(get("/api/v1/registry/bundles"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"gargantua-control-plane\""))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/v1/registry/bundles"));
    }

    @Test
    void aWrongTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/registry/bundles").header("Authorization", "Bearer nope"))
                .andExpect(status().isUnauthorized());
        // A prefix of a real token must not pass.
        mvc.perform(get("/api/v1/registry/bundles").header("Authorization", "Bearer admin-secre"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthAndTheRootIndexNeedNoToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void aReaderMayReadAndEvaluatePoliciesButNotChangeAnything() throws Exception {
        String reader = "Bearer reader-secret";
        mvc.perform(get("/api/v1/registry/bundles").header("Authorization", reader))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/policies/evaluate").header("Authorization", reader)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"target\":\"agent:x\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/registry/bundles").header("Authorization", reader)
                        .contentType(MediaType.APPLICATION_JSON).content(PUBLISH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(delete("/api/v1/registry/bundles/auth-agent/1.0.0").header("Authorization", reader))
                .andExpect(status().isForbidden());
    }

    @Test
    void anyConfiguredAdminTokenMayPublishAndTheSchemeIsCaseInsensitive() throws Exception {
        mvc.perform(post("/api/v1/registry/bundles").header("Authorization", "bearer second-admin")
                        .contentType(MediaType.APPLICATION_JSON).content(PUBLISH))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/registry/bundles/auth-agent/1.0.0/bundle")
                        .header("Authorization", "Bearer reader-secret"))
                .andExpect(status().isOk());
    }
}
