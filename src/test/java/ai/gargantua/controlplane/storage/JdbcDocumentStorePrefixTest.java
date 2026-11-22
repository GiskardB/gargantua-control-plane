package ai.gargantua.controlplane.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class JdbcDocumentStorePrefixTest {

    @Autowired
    private DocumentStore store;

    @Test
    void prefixMatchesLiterallyAndComesBackOrdered() {
        String c = "prefix-test";
        store.put(c, "a_b@2.0.0", "underscore-2");
        store.put(c, "a_b@1.0.0", "underscore-1");
        // Would match "a_b@" if '_' were left as a LIKE wildcard.
        store.put(c, "axb@1.0.0", "x");
        // Would match "100%@" if '%' were left as a LIKE wildcard.
        store.put(c, "100%@1", "percent");
        store.put(c, "100abc@1", "abc");

        assertThat(store.listByIdPrefix(c, "a_b@", String.class)).containsExactly("underscore-1", "underscore-2");
        assertThat(store.listByIdPrefix(c, "100%@", String.class)).containsExactly("percent");
        assertThat(store.listByIdPrefix(c, "nope@", String.class)).isEmpty();
    }
}
