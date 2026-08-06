package ai.gargantua.controlplane.registry;

import ai.gargantua.controlplane.catalog.CatalogService;
import ai.gargantua.controlplane.common.ConflictException;
import ai.gargantua.controlplane.common.NotFoundException;
import ai.gargantua.controlplane.domain.BundleDescriptor;
import ai.gargantua.controlplane.registry.model.BundleArtifact;
import ai.gargantua.controlplane.registry.model.Environment;
import ai.gargantua.controlplane.storage.BlobStore;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
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

    public RegistryService(
            Registry registry,
            BlobStore blobStore,
            ManifestReader manifestReader,
            CatalogService catalogService) {
        this.registry = registry;
        this.blobStore = blobStore;
        this.manifestReader = manifestReader;
        this.catalogService = catalogService;
    }

    /**
     * Validate and publish a manifest. Versions are immutable: republishing an
     * existing {@code name@version} is a conflict, not an overwrite.
     */
    public BundleArtifact publish(String manifest) {
        BundleDescriptor descriptor = manifestReader.read(manifest);
        if (registry.exists(descriptor.name(), descriptor.version())) {
            throw new ConflictException(
                    "bundle " + descriptor.coordinate() + " already published");
        }
        blobStore.put(blobKey(descriptor.name(), descriptor.version()),
                manifest.getBytes(StandardCharsets.UTF_8));

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

    /** Promote a published version to an environment (idempotent per environment). */
    public BundleArtifact promote(String name, String version, Environment environment) {
        BundleArtifact current = get(name, version);
        BundleArtifact promoted = current.promotedTo(environment);
        registry.update(promoted);
        return promoted;
    }

    private static String blobKey(String name, String version) {
        return name + "/" + version + "/manifest.yaml";
    }
}
