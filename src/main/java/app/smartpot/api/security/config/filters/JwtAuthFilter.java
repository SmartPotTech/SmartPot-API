package app.smartpot.api.security.config.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import app.smartpot.api.security.service.JwtServiceImpl;
import app.smartpot.api.users.model.dto.UserDTO;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    // TODO: implement role for jwt
    private final JwtServiceImpl jwtServiceImpl;

    public JwtAuthFilter(JwtServiceImpl jwtServiceImpl) {
        this.jwtServiceImpl = jwtServiceImpl;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        try {
            UserDTO user = jwtServiceImpl.validateAuthHeader(authHeader);
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    user, user.getPassword(), null /* user.getAuthorities() */);
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (Exception ignored) {
        }
        filterChain.doFilter(request, response);
    }
}