package smartpot.com.api.Security.Config.Filters;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
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

    private long windowStart = System.currentTimeMillis();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        String clientIP = request.getRemoteAddr();
        long currentTime = System.currentTimeMillis();

        if (currentTime - windowStart > TIME_WINDOW) {
            windowStart = currentTime;
            requestsCount.clear();
        }

        requestsCount.putIfAbsent(clientIP, new AtomicInteger(0));
        int currentCount = requestsCount.get(clientIP).incrementAndGet();

        if (currentCount >= MAX_REQUESTS) {
            ((HttpServletResponse) response).setStatus(429);
            response.getWriter().write("Haz enviado demasiadas solicitudes, intenta de nuevo mas tarde");
            return;
        }

        chain.doFilter(request, response);
    }
}
