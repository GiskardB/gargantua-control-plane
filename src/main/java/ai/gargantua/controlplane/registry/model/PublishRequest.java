package ai.gargantua.controlplane.registry.model;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request to publish a bundle. The body is the full {@code gargantua.ai/v1}
 * manifest as YAML (or JSON) text — the Control Plane derives the bundle name,
 * version, kind and capabilities from it, so nothing here is duplicated.
 *
 * @param manifest   the raw manifest document
 * @param skillFiles raw {@code SKILL.md} content per skill name; used to assemble the
 *                   runnable {@code .gbundle} zip. May be empty (manifest-only publish).
 */
public record PublishRequest(@NotBlank String manifest, Map<String, String> skillFiles) {

    public PublishRequest {
        skillFiles = skillFiles == null ? Map.of() : Map.copyOf(skillFiles);
    }
}
