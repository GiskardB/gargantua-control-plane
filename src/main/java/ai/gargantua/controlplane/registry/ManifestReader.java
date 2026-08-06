package ai.gargantua.controlplane.registry;

import ai.gargantua.controlplane.common.ValidationException;
import ai.gargantua.controlplane.domain.BundleDescriptor;
import ai.gargantua.controlplane.domain.Capability;
import ai.gargantua.controlplane.domain.WorkloadKind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Parses an uploaded {@code gargantua.ai/v1} manifest (YAML or JSON) into a
 * {@link BundleDescriptor}. This is the Control Plane's admission gate: it mirrors
 * the invariants the Runtime enforces when it constructs a manifest record, so a
 * bundle that publishes here is one the Runtime will accept.
 *
 * <p>It validates shape only — {@code apiVersion}, {@code kind}, required metadata —
 * not semantics; the Runtime remains the authority on execution.
 */
@Component
public class ManifestReader {

    /** The one apiVersion this Control Plane understands. */
    public static final String API_VERSION = "gargantua.ai/v1";

    private final YAMLMapper yaml = new YAMLMapper();
    private final Clock clock;

    public ManifestReader(Clock clock) {
        this.clock = clock;
    }

    /**
     * Validate the manifest text and derive its descriptor.
     *
     * @param manifest raw YAML or JSON
     * @return a descriptor with checksum over the exact bytes supplied
     * @throws ValidationException if the manifest is malformed or violates invariants
     */
    public BundleDescriptor read(String manifest) {
        if (manifest == null || manifest.isBlank()) {
            throw new ValidationException("manifest is empty");
        }
        JsonNode root = parse(manifest);

        String apiVersion = text(root, "apiVersion");
        if (!API_VERSION.equals(apiVersion)) {
            throw new ValidationException(
                    "unsupported apiVersion '" + apiVersion + "', expected " + API_VERSION);
        }

        WorkloadKind kind = parseKind(text(root, "kind"));

        JsonNode metadata = root.get("metadata");
        if (metadata == null || !metadata.isObject()) {
            throw new ValidationException("metadata is required");
        }
        String name = text(metadata, "name");
        if (name == null || name.isBlank()) {
            throw new ValidationException("metadata.name is required");
        }
        String version = text(metadata, "version");
        if (version == null || version.isBlank()) {
            throw new ValidationException("metadata.version is required");
        }

        Map<String, String> labels = readLabels(metadata.get("labels"));
        List<Capability> capabilities = readCapabilities(root.path("spec").get("capabilities"));
        String checksum = sha256(manifest.getBytes(StandardCharsets.UTF_8));

        return new BundleDescriptor(
                name, version, kind, checksum, capabilities, labels, clock.instant());
    }

    private JsonNode parse(String manifest) {
        try {
            return yaml.readTree(manifest);
        } catch (Exception e) {
            throw new ValidationException("manifest is not valid YAML/JSON: " + e.getMessage());
        }
    }

    private WorkloadKind parseKind(String kind) {
        if (kind == null || kind.isBlank()) {
            throw new ValidationException("kind is required");
        }
        try {
            return WorkloadKind.valueOf(kind.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("unknown kind '" + kind + "'");
        }
    }

    private Map<String, String> readLabels(JsonNode labels) {
        if (labels == null || !labels.isObject()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = labels.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            out.put(e.getKey(), e.getValue().asText());
        }
        return out;
    }

    private List<Capability> readCapabilities(JsonNode caps) {
        List<Capability> out = new ArrayList<>();
        if (caps == null || !caps.isArray()) {
            return out;
        }
        for (JsonNode c : caps) {
            // A capability may be a bare string (name only) or an object.
            if (c.isTextual()) {
                out.add(new Capability(c.asText(), null, null, null, null, List.of()));
                continue;
            }
            String name = text(c, "name");
            if (name == null || name.isBlank()) {
                throw new ValidationException("spec.capabilities[].name is required");
            }
            List<String> tags = new ArrayList<>();
            JsonNode tagsNode = c.get("tags");
            if (tagsNode != null && tagsNode.isArray()) {
                tagsNode.forEach(t -> tags.add(t.asText()));
            }
            out.add(new Capability(
                    name,
                    text(c, "description"),
                    text(c, "version"),
                    nodeToString(c.get("inputSchema")),
                    nodeToString(c.get("outputSchema")),
                    tags));
        }
        return out;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private String nodeToString(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        try {
            return new ObjectMapper().writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
