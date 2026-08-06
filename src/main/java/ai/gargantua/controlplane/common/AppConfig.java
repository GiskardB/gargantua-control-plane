package ai.gargantua.controlplane.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shared beans. A single {@link Clock} makes time injectable and tests deterministic. */
@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Dedicated Jackson 2 mapper for the document store. Kept separate from the HTTP
     * layer (Spring Boot 4 serves JSON with Jackson 3) so persistence has a stable,
     * self-contained encoding: ISO-8601 timestamps and records handled natively.
     */
    @Bean
    public ObjectMapper documentObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
