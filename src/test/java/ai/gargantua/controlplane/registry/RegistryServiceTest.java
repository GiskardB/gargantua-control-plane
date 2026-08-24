package ai.gargantua.controlplane.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.gargantua.controlplane.catalog.CatalogService;
import ai.gargantua.controlplane.catalog.InMemoryCatalog;
import ai.gargantua.controlplane.catalog.model.CapabilityEntry;
import ai.gargantua.controlplane.common.ConflictException;
import ai.gargantua.controlplane.common.NotFoundException;
import ai.gargantua.controlplane.common.ValidationException;
import ai.gargantua.core.workload.WorkloadKind;
import ai.gargantua.controlplane.registry.model.BundleArtifact;
import ai.gargantua.controlplane.registry.model.Environment;
import ai.gargantua.controlplane.storage.FilesystemBlobStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegistryServiceTest {

    private static final String MANIFEST = """
            apiVersion: gargantua.ai/v1
            kind: Agent
            metadata:
              name: summarizer
              version: 1.0.0
              labels:
                team: platform
            spec:
              capabilities:
                - name: summarize-document
                  description: Condense a document into key points
                  tags: [text, nlp]
            """;

    private RegistryService service;
    private CatalogService catalogService;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        catalogService = new CatalogService(new InMemoryCatalog(), clock);
        service = new RegistryService(
                new InMemoryRegistry(),
                new FilesystemBlobStore(tmp.toString()),
                new ManifestReader(clock),
                catalogService);
    }

    @Test
    void publishParsesManifestAndIndexesCapabilities() {
        BundleArtifact artifact = service.publish(MANIFEST);

        assertThat(artifact.descriptor().name()).isEqualTo("summarizer");
        assertThat(artifact.descriptor().version()).isEqualTo("1.0.0");
        assertThat(artifact.descriptor().kind()).isEqualTo(WorkloadKind.AGENT);
        assertThat(artifact.descriptor().checksum()).hasSize(64);
        assertThat(artifact.descriptor().labels()).containsEntry("team", "platform");
        assertThat(artifact.environments()).isEmpty();

        List<CapabilityEntry> caps = catalogService.capabilities();
        assertThat(caps).singleElement()
                .satisfies(c -> {
                    assertThat(c.name()).isEqualTo("summarize-document");
                    assertThat(c.providers()).containsExactly("summarizer@1.0.0");
                });
    }

    @Test
    void republishingSameVersionConflicts() {
        service.publish(MANIFEST);
        assertThatThrownBy(() -> service.publish(MANIFEST))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsWrongApiVersion() {
        String bad = MANIFEST.replace("gargantua.ai/v1", "gargantua.ai/v2");
        assertThatThrownBy(() -> service.publish(bad))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void storesAndReturnsManifestBytes() {
        service.publish(MANIFEST);
        String stored = service.manifest("summarizer", "1.0.0");
        assertThat(stored).isEqualTo(MANIFEST);
    }

    @Test
    void storesRunnableBundleZipWithSkills() throws Exception {
        service.publish(MANIFEST, java.util.Map.of("greeter-skill", "---\nname: greeter-skill\n---\nHi"));

        byte[] zip = service.bundleZip("summarizer", "1.0.0");
        java.util.Map<String, String> entries = new java.util.HashMap<>();
        try (var zin = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            java.util.zip.ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                entries.put(e.getName(), new String(zin.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        assertThat(entries).containsKeys("manifest.yaml", "skills/greeter-skill/SKILL.md");
        assertThat(entries.get("manifest.yaml")).isEqualTo(MANIFEST);
        assertThat(entries.get("skills/greeter-skill/SKILL.md")).contains("greeter-skill");
    }

    @Test
    void bundleZipMissingForUnknownVersionIsNotFound() {
        assertThatThrownBy(() -> service.bundleZip("nope", "1.0.0"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void promoteAdvancesEnvironments() {
        service.publish(MANIFEST);
        BundleArtifact promoted = service.promote("summarizer", "1.0.0", Environment.STAGING);
        assertThat(promoted.environments()).containsExactly(Environment.STAGING);

        // Persisted, and idempotent additivity across environments.
        service.promote("summarizer", "1.0.0", Environment.PRODUCTION);
        assertThat(service.get("summarizer", "1.0.0").environments())
                .containsExactlyInAnyOrder(Environment.STAGING, Environment.PRODUCTION);
    }

    @Test
    void unknownBundleIsNotFound() {
        assertThatThrownBy(() -> service.get("nope", "1.0.0"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.versions("nope"))
                .isInstanceOf(NotFoundException.class);
    }
}
