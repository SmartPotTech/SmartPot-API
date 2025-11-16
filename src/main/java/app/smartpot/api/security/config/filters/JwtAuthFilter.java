package app.smartpot.api.security.config.filters;

import app.smartpot.api.security.service.JwtServiceImpl;
import app.smartpot.api.users.model.dto.UserDTO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    // TODO: implement role for jwt
    private final JwtServiceImpl jwtServiceImpl;

    @Value("${application.security.public.routes}")
    private String publicRoutes;
    private List<String> publicRoutesList;

    public JwtAuthFilter(JwtServiceImpl jwtServiceImpl) {
        this.jwtServiceImpl = jwtServiceImpl;
    }

    @Override
    public void afterPropertiesSet() {
        if (publicRoutes != null && !publicRoutes.isEmpty()) {
            publicRoutesList = Arrays.stream(publicRoutes.split(","))
                    .map(route -> route.replace("/**", ""))
                    .toList();
        }
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        for (String route : publicRoutesList) {
            if (request.getServletPath().startsWith(route)) {
                filterChain.doFilter(request, response);
                return;
            }
        }
        String authHeader = request.getHeader("Authorization");
        try {
            UserDTO user = jwtServiceImpl.validateAuthHeader(authHeader);
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    user, user.getPassword(), null /* user.getAuthorities() */);
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token Invalido o Expirado");
        }
    }
}