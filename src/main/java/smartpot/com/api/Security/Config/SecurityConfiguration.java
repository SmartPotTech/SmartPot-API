package smartpot.com.api.Security.Config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import smartpot.com.api.Users.Model.DAO.Service.SUser;
import smartpot.com.api.Security.Config.headers.CorsConfig;
import smartpot.com.api.Security.Config.jwt.JwtAuthFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
public class SecurityConfiguration {

    private final CorsConfig corsConfig;
    private final JwtAuthFilter jwtAuthFilter;
    private final SUser serviceUser;

    public SecurityConfiguration(CorsConfig corsConfig, JwtAuthFilter jwtAuthFilter, SUser serviceUser) {
        this.corsConfig = corsConfig;
        this.jwtAuthFilter = jwtAuthFilter;
        this.serviceUser = serviceUser;
    }

    @Value("${application.security.public.routes}")
    private String publicRoutes;

    /**
     * Configura la seguridad de las solicitudes HTTP para la aplicación.
     * Este bean define las políticas de autorización, autenticación y manejo de sesiones.
     *
     * @param httpSec La configuración de seguridad HTTP.
     * @return Un objeto {@link SecurityFilterChain} configurado con las políticas de seguridad.
     * @throws Exception Sí ocurre un error durante la configuración.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSec) throws Exception {

        // Public Routes
        List<String> publicRoutesList;
        if (publicRoutes.contains(",")) {
            publicRoutesList = Arrays.asList(publicRoutes.split(","));
        } else {
            publicRoutesList = List.of(publicRoutes);
        }

        return httpSec
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(corsConfig))
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry -> {
                    authorizationManagerRequestMatcherRegistry.requestMatchers(publicRoutesList.toArray(new String[0])).permitAll();
                    authorizationManagerRequestMatcherRegistry.anyRequest().authenticated();
                })
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(httpSecuritySessionManagementConfigurer -> httpSecuritySessionManagementConfigurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Configura un proveedor de autenticación utilizando DaoAuthenticationProvider.
     * Este bean es responsable de autenticar a los usuarios basándose en los datos de usuario proporcionados por {@link SUser}.
     *
     * @return Un objeto {@link AuthenticationProvider} configurado.
     */
    @Bean
    AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(serviceUser);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Configura un codificador de contraseñas utilizando {@link BCryptPasswordEncoder}.
     * Este bean es responsable de encriptar las contraseñas de los usuarios.
     *
     * @return Un objeto {@link PasswordEncoder} que usa BCrypt para encriptar las contraseñas.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}