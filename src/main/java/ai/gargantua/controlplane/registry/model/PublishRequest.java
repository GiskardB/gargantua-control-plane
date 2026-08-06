package ai.gargantua.controlplane.registry.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to publish a bundle. The body is the full {@code gargantua.ai/v1}
 * manifest as YAML (or JSON) text — the Control Plane derives the bundle name,
 * version, kind and capabilities from it, so nothing here is duplicated.
 *
 * @param manifest the raw manifest document
 */
public record PublishRequest(@NotBlank String manifest) {
}
