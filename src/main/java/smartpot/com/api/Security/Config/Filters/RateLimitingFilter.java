package smartpot.com.api.Security.Config.Filters;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitingFilter implements Filter {

    private final Map<String, AtomicInteger> requestsCount = new ConcurrentHashMap<>();

    @Value("${rate.limiting.max-requests}")
    private int MAX_REQUESTS;

    @Value("${rate.limiting.time-window}")
    private long TIME_WINDOW;

    @Value("${rate.limiting.public-routes}")
    private String publicRoutes;

    private long windowStart = System.currentTimeMillis();

    private List<String> publicRoutesList;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Convertir el string de rutas públicas en una lista
        if (publicRoutes != null && !publicRoutes.isEmpty()) {
            publicRoutesList = Arrays.asList(publicRoutes.split(","));
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        if (isPublicRoute(httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String clientIP = request.getRemoteAddr();
        long currentTime = System.currentTimeMillis();

        if (currentTime - windowStart > TIME_WINDOW) {
            windowStart = currentTime;
            requestsCount.clear();
        }

        requestsCount.putIfAbsent(clientIP, new AtomicInteger(0));
        int currentCount = requestsCount.get(clientIP).incrementAndGet();

        if (currentCount >= MAX_REQUESTS) {
            ((HttpServletResponse) response).setStatus(429);  // Too Many Requests
            response.getWriter().write("Haz enviado demasiadas solicitudes, intenta de nuevo más tarde");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isPublicRoute(String uri) {
        for (String route : publicRoutesList) {
            if (uri.startsWith(route)) {
                return true;
            }
        }
        return false;
    }
}
