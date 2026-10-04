package com.clubhub.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity // enables @PreAuthorize for per-endpoint club role checks
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Stateless JSON API authenticated by bearer tokens: no cookies/session, so no CSRF.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                // Deny by default: every endpoint needs a valid token unless listed here.
                .authorizeHttpRequests(auth -> auth
                        // refresh/logout authenticate with the refresh token in the body, not a bearer token
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/logout", "/api/auth/verify-email",
                                "/api/auth/resend-verification", "/api/auth/forgot-password",
                                "/api/auth/reset-password").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                        // certificate verification links are printed on PDFs and opened by recruiters
                        .requestMatchers(HttpMethod.GET, "/api/verify/**").permitAll()
                        // the campus directory is public to browse (approved content only);
                        // suggesting something still needs an account (POST stays authenticated)
                        .requestMatchers(HttpMethod.GET, "/api/campus/**").permitAll()
                        // WebSocket handshake can't carry a bearer header: STOMP CONNECT is authenticated
                        // instead (StompAuthInterceptor), so no frame is processed without a valid token
                        .requestMatchers(HttpMethod.GET, "/ws").permitAll()
                        .requestMatchers("/error").permitAll()
                        // API documentation (the spec itself holds no data)
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html",
                                "/swagger-ui/**").permitAll()
                        // platform administration: only the ROLE_PLATFORM_ADMIN authority (from the "roles" claim)
                        .requestMatchers("/api/platform/**").hasRole("PLATFORM_ADMIN")
                        .anyRequest().authenticated())
                // Validates "Authorization: Bearer <jwt>" with our JwtDecoder (signature, exp, issuer)
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    /** Maps the token's "roles" claim (e.g. ["PLATFORM_ADMIN"]) to Spring authorities ROLE_PLATFORM_ADMIN. */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtTokenService.CLAIM_ROLES);
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * BCrypt by default, but hashes are stored with an {id} prefix ("{bcrypt}...") so the
     * algorithm can be upgraded later without invalidating existing passwords.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
