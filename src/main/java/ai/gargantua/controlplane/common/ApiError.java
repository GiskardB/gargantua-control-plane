package ai.gargantua.controlplane.common;

import java.time.Instant;

/**
 * The uniform error body every failing endpoint returns.
 *
 * @param status    HTTP status code
 * @param error     short reason phrase, e.g. {@code Not Found}
 * @param message   human-readable detail
 * @param path      the request path that failed
 * @param timestamp when the error was produced
 */
public record ApiError(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp) {
}
