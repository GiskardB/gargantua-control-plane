package ai.gargantua.controlplane.registry;

import ai.gargantua.controlplane.catalog.CatalogService;
import ai.gargantua.controlplane.common.ConflictException;
import ai.gargantua.controlplane.common.NotFoundException;
import ai.gargantua.controlplane.deployment.DeploymentManager;
import ai.gargantua.controlplane.registry.model.BundleArtifact;
import ai.gargantua.controlplane.registry.model.Environment;
import ai.gargantua.controlplane.registry.model.PublishedBundle;
import ai.gargantua.controlplane.storage.BlobStore;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

/**
 * The Registry subsystem's use cases: publish a bundle, list and fetch versions,
 * retrieve the stored manifest, and promote a version through environments.
 *
 * <p>Publishing is the pivotal operation — it validates the manifest, records the
 * descriptor, stores the raw bytes, and hands the declared capabilities to the
 * Catalog so discovery stays in step with what has been published.
 */
@Service
public class RegistryService {

    private final Registry registry;
    private final BlobStore blobStore;
    private final ManifestReader manifestReader;
    private final CatalogService catalogService;
    private final DeploymentManager deploymentManager;

    public RegistryService(
            Registry registry,
            BlobStore blobStore,
            ManifestReader manifestReader,
            CatalogService catalogService,
            DeploymentManager deploymentManager) {
        this.registry = registry;
        this.blobStore = blobStore;
        this.manifestReader = manifestReader;
        this.catalogService = catalogService;
        this.deploymentManager = deploymentManager;
    }

    /**
     * Validate and publish a manifest. Versions are immutable: republishing an
     * existing {@code name@version} is a conflict, not an overwrite.
     */
    public BundleArtifact publish(String manifest) {
        return publish(manifest, Map.of(), Map.of());
    }

    /** Publish a manifest together with the raw {@code SKILL.md} content of each skill. */
    public BundleArtifact publish(String manifest, Map<String, String> skillFiles) {
        return publish(manifest, skillFiles, Map.of());
    }

    /**
     * Publish a manifest together with the raw {@code SKILL.md} content of each skill
     * (keyed by skill name) and, per skill, any text reference files. In addition to the
     * manifest blob, this stores a runnable {@code .gbundle} zip (manifest +
     * {@code skills/<name>/SKILL.md} + {@code skills/<name>/references/<filename>}) that
     * a Runtime can download and execute directly — the reference files are what the
     * Runtime's FilesystemSkillRegistry reads and appends to each skill's references.
     */
    public BundleArtifact publish(
            String manifest, Map<String, String> skillFiles, Map<String, Map<String, String>> skillReferenceFiles) {
        PublishedBundle descriptor = manifestReader.read(manifest);
        if (registry.exists(descriptor.name(), descriptor.version())) {
            throw new ConflictException(
                    "bundle " + descriptor.coordinate() + " already published");
        }
        blobStore.put(blobKey(descriptor.name(), descriptor.version()),
                manifest.getBytes(StandardCharsets.UTF_8));
        blobStore.put(bundleKey(descriptor.name(), descriptor.version()),
                zipBundle(manifest, skillFiles, skillReferenceFiles));

        BundleArtifact artifact = BundleArtifact.of(descriptor);
        registry.save(artifact);
        catalogService.indexBundle(descriptor);
        return artifact;
    }

    public List<BundleArtifact> list() {
        return registry.findAll().stream()
                .sorted(Comparator
                        .comparing((BundleArtifact a) -> a.descriptor().name())
                        .thenComparing(a -> a.descriptor().version()))
                .toList();
    }

    public List<BundleArtifact> versions(String name) {
        List<BundleArtifact> found = registry.findByName(name);
        if (found.isEmpty()) {
            throw new NotFoundException("no bundle named '" + name + "'");
        }
        return found.stream()
                .sorted(Comparator.comparing(a -> a.descriptor().version()))
                .toList();
    }

    public BundleArtifact get(String name, String version) {
        return registry.find(name, version)
                .orElseThrow(() -> new NotFoundException(
                        "bundle " + name + "@" + version + " not found"));
    }

    public String manifest(String name, String version) {
        get(name, version); // 404 if the version is unknown
        return blobStore.get(blobKey(name, version))
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .orElseThrow(() -> new NotFoundException(
                        "manifest bytes missing for " + name + "@" + version));
    }

    /** The runnable {@code .gbundle} zip bytes for a published version. */
    public byte[] bundleZip(String name, String version) {
        get(name, version); // 404 if the version is unknown
        return blobStore.get(bundleKey(name, version))
                .orElseThrow(() -> new NotFoundException(
                        "bundle zip missing for " + name + "@" + version));
    }

    /** Promote a published version to an environment (idempotent per environment). */
    public BundleArtifact promote(String name, String version, Environment environment) {
        BundleArtifact current = get(name, version);
        BundleArtifact promoted = current.promotedTo(environment);
        registry.update(promoted);
        return promoted;
    }

    /**
     * Delete a published version. Refuses while any deployment still references it —
     * undeploy first so a Runtime never chases a bundle zip that has vanished.
     */
    public void delete(String name, String version) {
        get(name, version); // 404 if the version is unknown
        boolean stillDeployed = deploymentManager.findAll().stream()
                .anyMatch(d -> d.bundleName().equals(name) && d.bundleVersion().equals(version));
        if (stillDeployed) {
            throw new ConflictException(
                    "bundle " + name + "@" + version + " is still deployed; undeploy before deleting");
        }
        blobStore.delete(blobKey(name, version));
        blobStore.delete(bundleKey(name, version));
        registry.delete(name, version);
    }

    private static String blobKey(String name, String version) {
        return name + "/" + version + "/manifest.yaml";
    }

    private static String bundleKey(String name, String version) {
        return name + "/" + version + "/bundle.gbundle";
    }

    private static byte[] zipBundle(
            String manifest, Map<String, String> skillFiles, Map<String, Map<String, String>> skillReferenceFiles) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("manifest.yaml"));
            zip.write(manifest.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (skillFiles != null) {
                for (Map.Entry<String, String> skill : skillFiles.entrySet()) {
                    zip.putNextEntry(new ZipEntry("skills/" + skill.getKey() + "/SKILL.md"));
                    zip.write(skill.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            if (skillReferenceFiles != null) {
                for (Map.Entry<String, Map<String, String>> skill : skillReferenceFiles.entrySet()) {
                    for (Map.Entry<String, String> file : skill.getValue().entrySet()) {
                        zip.putNextEntry(new ZipEntry(
                                "skills/" + skill.getKey() + "/references/" + file.getKey()));
                        zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                        zip.closeEntry();
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot build bundle zip", e);
        }
        return out.toByteArray();
    }
}
