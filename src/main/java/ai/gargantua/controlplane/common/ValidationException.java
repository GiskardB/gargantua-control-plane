package ai.gargantua.controlplane.common;

/** Thrown when a submitted manifest or request is malformed; mapped to HTTP 400. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
