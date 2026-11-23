package ai.gargantua.controlplane.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;

/**
 * Bearer-token authentication for the Control Plane API.
 *
 * <p>Two kinds of token, both plain shared secrets supplied by configuration:
 * <ul>
 *   <li><b>reader</b> — may read: every {@code GET}, plus {@code POST
 *       /api/v1/policies/evaluate}, which changes nothing. This is what a Runtime needs to
 *       download its bundle.</li>
 *   <li><b>admin</b> — may also publish, promote, delete, register and deploy. This is
 *       what Studio needs.</li>
 * </ul>
 *
 * <p>With no token configured the API is open, as it was before this filter existed, and
 * a warning is logged at startup: fine for a laptop, not for a shared environment. Health
 * and the root index stay open either way so probes need no credential.
 *
 * <p>This is deliberately the smallest thing that stops an anonymous caller from
 * publishing or deleting bundles. It is not per-user identity, and it does not enforce
 * the policies the Policy Manager stores.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiTokenFilter.class);
    private static final String BEARER = "Bearer ";

    private final List<byte[]> adminTokens;
    private final List<byte[]> readerTokens;

    public ApiTokenFilter(
            @Value("${gargantua.security.admin-tokens:}") List<String> adminTokens,
            @Value("${gargantua.security.reader-tokens:}") List<String> readerTokens) {
        this.adminTokens = bytes(adminTokens);
        this.readerTokens = bytes(readerTokens);
        if (!enabled()) {
            log.warn("Control Plane API is UNAUTHENTICATED: no gargantua.security.admin-tokens / "
                    + "reader-tokens configured. Anyone who can reach this port can publish and "
                    + "delete bundles. Set GARGANTUA_SECURITY_ADMIN_TOKENS before sharing it.");
        } else if (this.adminTokens.isEmpty()) {
            log.warn("Control Plane has reader tokens but no admin token: the API is read-only.");
        }
    }

    private static List<byte[]> bytes(List<String> tokens) {
        return tokens == null ? List.of() : tokens.stream()
                .map(String::strip)
                .filter(t -> !t.isEmpty())
                .map(t -> t.getBytes(StandardCharsets.UTF_8))
                .toList();
    }

    boolean enabled() {
        return !adminTokens.isEmpty() || !readerTokens.isEmpty();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled() || "OPTIONS".equals(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        byte[] presented = header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())
                ? header.substring(BEARER.length()).strip().getBytes(StandardCharsets.UTF_8)
                : null;

        boolean admin = presented != null && matchesAny(adminTokens, presented);
        boolean reader = admin || (presented != null && matchesAny(readerTokens, presented));
        if (!reader) {
            response.setHeader("WWW-Authenticate", "Bearer realm=\"gargantua-control-plane\"");
            reject(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                    presented == null ? "a bearer token is required" : "the bearer token is not valid");
            return;
        }
        if (!admin && !isRead(request)) {
            reject(request, response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                    "this token may only read; an admin token is required to change state");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isRead(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method)) {
            return true;
        }
        // Evaluating a policy is a question, not a change.
        return "POST".equals(method) && request.getRequestURI().endsWith("/api/v1/policies/evaluate");
    }

    /** Compares against every token without stopping early, so timing reveals nothing. */
    private static boolean matchesAny(List<byte[]> tokens, byte[] presented) {
        boolean match = false;
        for (byte[] token : tokens) {
            match |= MessageDigest.isEqual(token, presented);
        }
        return match;
    }

    /** Same body shape as {@code ApiError}, written by hand: this runs before Spring MVC. */
    private static void reject(HttpServletRequest request, HttpServletResponse response,
                               int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status
                + ",\"error\":\"" + error + "\""
                + ",\"message\":\"" + message + "\""
                + ",\"path\":\"" + request.getRequestURI().replace("\\", "\\\\").replace("\"", "\\\"") + "\""
                + ",\"timestamp\":\"" + Instant.now() + "\"}");
    }
}
