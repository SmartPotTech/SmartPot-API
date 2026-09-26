package app.smartpot.api.security.config.filters;

import app.smartpot.api.cache.CacheStore;
import app.smartpot.api.config.SmartPotProperties;
import app.smartpot.api.security.config.JsonErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

/**
 * Ventana fija de un minuto por IP. Las rutas de autenticación tienen un cupo menor
 * para frenar ataques de fuerza bruta. Se instancia en la configuración de seguridad.
 */
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final CacheStore cacheStore;
    private final JsonErrorWriter errorWriter;
    private final Clock clock;
    private final int generalLimit;
    private final int authLimit;

    public RateLimitingFilter(CacheStore cacheStore, JsonErrorWriter errorWriter, Clock clock, SmartPotProperties properties) {
        this.cacheStore = cacheStore;
        this.errorWriter = errorWriter;
        this.clock = clock;
        this.generalLimit = properties.security().rateLimitPerMinute();
        this.authLimit = properties.security().authRateLimitPerMinute();
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod()) || path.equals("/health");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        boolean authRoute = request.getRequestURI().startsWith("/api/v1/auth/");
        int limit = authRoute ? authLimit : generalLimit;
        long minute = clock.instant().getEpochSecond() / 60;
        String key = "rate:" + (authRoute ? "auth:" : "all:") + request.getRemoteAddr() + ":" + minute;

        long count = cacheStore.increment(key, WINDOW);
        if (count > limit) {
            long retryAfter = 60 - clock.instant().getEpochSecond() % 60;
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            errorWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                    "Enviaste demasiadas solicitudes. Intenta de nuevo en " + retryAfter + " segundos");
            return;
        }
        chain.doFilter(request, response);
    }
}
