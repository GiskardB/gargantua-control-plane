package ai.gargantua.controlplane.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * S3-compatible {@link BlobStore} backed by MinIO — the durable object-storage
 * backend for multi-node deployments. Activated by the {@code minio} profile; the
 * default remains {@link FilesystemBlobStore}. Same port, so the Registry is
 * unaware which one is wired (ADR-004).
 *
 * <p>Configuration lives under {@code gargantua.storage.s3.*} (see
 * application-minio.yml). The bucket is created on startup if missing.
 */
@Component
@Profile("minio")
public class S3BlobStore implements BlobStore {

    private final MinioClient client;
    private final String bucket;

    public S3BlobStore(
            @Value("${gargantua.storage.s3.endpoint}") String endpoint,
            @Value("${gargantua.storage.s3.access-key}") String accessKey,
            @Value("${gargantua.storage.s3.secret-key}") String secretKey,
            @Value("${gargantua.storage.s3.bucket:gargantua-bundles}") String bucket) {
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
        ensureBucket();
    }

    private void ensureBucket() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot initialize S3 bucket '" + bucket + "'", e);
        }
    }

    @Override
    public void put(String key, byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(in, (long) bytes.length, -1L)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("cannot write blob: " + key, e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        try (InputStream in = client.getObject(
                GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return Optional.of(in.readAllBytes());
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return Optional.empty();
            }
            throw new IllegalStateException("cannot read blob: " + key, e);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read blob: " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new IllegalStateException("cannot stat blob: " + key, e);
        } catch (Exception e) {
            throw new IllegalStateException("cannot stat blob: " + key, e);
        }
    }
}
