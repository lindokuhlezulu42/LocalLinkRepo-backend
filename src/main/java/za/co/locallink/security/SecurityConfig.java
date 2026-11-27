package za.co.locallink.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DaoAuthenticationProvider authenticationProvider,
            @org.springframework.beans.factory.annotation.Value("${locallink.frontend-origin}") String configuredOrigin) throws Exception {

        // FRONTEND_ORIGIN may hold several comma-separated origins; tolerate stray spaces,
        // trailing slashes and a missing scheme so a small typo doesn't block every request.
        var allowedOrigins = new java.util.ArrayList<>(java.util.List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:5174",
                "http://127.0.0.1:5174"));
        for (var origin : configuredOrigin.split(",")) {
            var normalized = origin.trim().replaceAll("/+$", "");
            if (normalized.isEmpty()) continue;
            if (!normalized.contains("://")) normalized = "https://" + normalized;
            allowedOrigins.add(normalized);
        }
        var csrfRepository = new HttpSessionCsrfTokenRepository();
        csrfRepository.setHeaderName("X-XSRF-TOKEN");
        var csrfHandler = new CsrfTokenRequestAttributeHandler();

        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfHandler))
                .cors(cors -> cors.configurationSource(request -> {
                    var configuration = new org.springframework.web.cors.CorsConfiguration();
                    configuration.setAllowedOrigins(allowedOrigins);
                    configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                    configuration.setAllowedHeaders(java.util.List.of("*")); // Allow all headers for local dev
                    configuration.setAllowCredentials(true);
                    return configuration;
                }))
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/csrf", "/api/auth/me", "/api/auth/register", "/api/auth/login",
                                "/api/auth/verify-email", "/api/auth/resend-verification", "/api/auth/forgot-password",
                                "/api/auth/reset-password", "/api/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/providers/mine").authenticated()
                        .requestMatchers(new AntPathRequestMatcher("/api/providers", HttpMethod.GET.name())).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/providers/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/enquiries/*/review").hasRole("RESIDENT")
                        .requestMatchers(HttpMethod.PATCH, "/api/enquiries/*/status").hasRole("PROVIDER")
                        .requestMatchers(HttpMethod.POST, "/api/providers/*/availability").hasRole("PROVIDER")
                        .requestMatchers(HttpMethod.GET, "/api/availability/provider").hasRole("PROVIDER")
                        .requestMatchers(HttpMethod.DELETE, "/api/availability/*").hasRole("PROVIDER")
                        .requestMatchers(HttpMethod.POST, "/api/availability/*/bookings").hasRole("RESIDENT")
                        .requestMatchers(HttpMethod.GET, "/api/bookings/mine").hasRole("RESIDENT")
                        .requestMatchers(HttpMethod.GET, "/api/bookings/provider").hasRole("PROVIDER")
                        .requestMatchers(HttpMethod.PATCH, "/api/bookings/*/status").hasRole("PROVIDER")
                        .requestMatchers(HttpMethod.POST, "/api/providers/*/enquiries").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/enquiries/mine").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/enquiries/mine/*").hasRole("RESIDENT")
                        .requestMatchers(HttpMethod.DELETE, "/api/enquiries/**").hasRole("PROVIDER")
                        .requestMatchers(HttpMethod.POST, "/api/providers").hasRole("PROVIDER")
                        .requestMatchers(HttpMethod.PATCH, "/api/providers/**").hasRole("PROVIDER")
                        .requestMatchers(HttpMethod.DELETE, "/api/providers/**").hasRole("PROVIDER")
                        .anyRequest().authenticated())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }
}