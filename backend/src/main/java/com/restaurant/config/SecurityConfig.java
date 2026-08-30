package com.restaurant.config;

import com.restaurant.auth.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No autenticado"))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Acceso denegado"))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/ws/**", "/ws-native/**").permitAll()
                .requestMatchers("/api/auth/login", "/api/auth/health").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/actuator/health").permitAll()

                // Cliente de mesa: no tiene JWT, pero las mutaciones por pedido validan sessionId.
                .requestMatchers(HttpMethod.POST, "/api/chat/turn").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/chat/history/**").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/chat/history/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/orders/sessions/table/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/orders/table/*/draft", "/api/orders/table/*/current").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/orders/sessions/*/call-waiter", "/api/orders/sessions/*/request-payment").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/orders/*/confirm", "/api/orders/*/clear", "/api/orders/*/items/*/cancel").permitAll()

                // Catálogo público de solo lectura. Mutaciones: ADMIN.
                .requestMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/catalog/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/catalog/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/catalog/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/catalog/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.GET, "/api/tables/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/tables/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/tables/**").hasRole("ADMIN")

                // Staff.
                .requestMatchers("/api/orders/**").authenticated()
                .requestMatchers("/api/inventory/**").authenticated()
                .requestMatchers("/api/reports/**", "/api/reportes/**", "/api/audit/**", "/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://10.*:*",
            "http://172.16.*:*",
            "http://172.17.*:*",
            "http://172.18.*:*",
            "http://172.19.*:*",
            "http://172.20.*:*",
            "http://172.21.*:*",
            "http://172.22.*:*",
            "http://172.23.*:*",
            "http://172.24.*:*",
            "http://172.25.*:*",
            "http://172.26.*:*",
            "http://172.27.*:*",
            "http://172.28.*:*",
            "http://172.29.*:*",
            "http://172.30.*:*",
            "http://172.31.*:*",
            "http://192.168.*:*",
            "https://*.loca.lt",
            "https://*.ngrok-free.app",
            "https://*.ngrok.io"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
