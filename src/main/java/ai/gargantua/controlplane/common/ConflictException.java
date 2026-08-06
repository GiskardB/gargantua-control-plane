package ai.gargantua.controlplane.common;

/** Thrown when a mutation would violate uniqueness (e.g. republishing an
 * immutable version); mapped to HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
