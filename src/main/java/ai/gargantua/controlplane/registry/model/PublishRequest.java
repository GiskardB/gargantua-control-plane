package ai.gargantua.controlplane.registry.model;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request to publish a bundle. The body is the full {@code gargantua.ai/v1}
 * manifest as YAML (or JSON) text — the Control Plane derives the bundle name,
 * version, kind and capabilities from it, so nothing here is duplicated.
 *
 * @param manifest            the raw manifest document
 * @param skillFiles          raw {@code SKILL.md} content per skill name; used to assemble
 *                            the runnable {@code .gbundle} zip. May be empty (manifest-only
 *                            publish).
 * @param skillReferenceFiles per skill name, a map of filename to raw text content, written
 *                            to {@code skills/<name>/references/<filename>} in the zip — the
 *                            Runtime's FilesystemSkillRegistry reads every file under that
 *                            folder and appends its content to the skill's references. May
 *                            be empty or omit a skill entirely (no reference files for it).
 */
public record PublishRequest(
        @NotBlank String manifest,
        Map<String, String> skillFiles,
        Map<String, Map<String, String>> skillReferenceFiles) {

    public PublishRequest {
        skillFiles = skillFiles == null ? Map.of() : Map.copyOf(skillFiles);
        skillReferenceFiles = skillReferenceFiles == null ? Map.of() : Map.copyOf(skillReferenceFiles);
    }

    /** Back-compat overload for callers that predate per-skill reference files. */
    public PublishRequest(String manifest, Map<String, String> skillFiles) {
        this(manifest, skillFiles, Map.of());
    }
}
