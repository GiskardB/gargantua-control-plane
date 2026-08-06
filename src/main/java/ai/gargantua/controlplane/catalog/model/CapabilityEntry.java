package ai.gargantua.controlplane.catalog.model;

import java.util.List;

/**
 * The Catalog's view of a capability: what it is, and which published bundles
 * provide it. This is the answer to "who can do X" — the basis of discovery.
 *
 * @param name        capability name
 * @param description last-seen description for the capability
 * @param providers   bundle coordinates ({@code name@version}) that declare it
 */
public record CapabilityEntry(
        String name,
        String description,
        List<String> providers) {

    public CapabilityEntry {
        providers = providers == null ? List.of() : List.copyOf(providers);
    }
}
