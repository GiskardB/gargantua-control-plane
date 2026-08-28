package ai.gargantua.controlplane.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Filesystem-backed {@link BlobStore} — writes each blob as a file under a configured
 * root directory; the key becomes a relative path. Use the {@code filesystem} profile
 * to activate this implementation explicitly; the default remains {@link PostgresBlobStore}.
 */
@Component
@Profile("filesystem")
public class FilesystemBlobStore implements BlobStore {

    private final Path root;

    public FilesystemBlobStore(@Value("${gargantua.storage.blob-dir:./data/blobs}") String blobDir) {
        this.root = Path.of(blobDir);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create blob store root: " + root, e);
        }
    }

    @Override
    public void put(String key, byte[] bytes) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write blob: " + key, e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        Path target = resolve(key);
        if (!Files.exists(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(target));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read blob: " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot delete blob: " + key, e);
        }
    }

    /** Resolve a key under root, refusing traversal outside it. */
    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root.normalize())) {
            throw new IllegalArgumentException("blob key escapes storage root: " + key);
        }
        return target;
    }
}
