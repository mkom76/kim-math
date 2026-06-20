package com.example.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.repository.StudentRepository;
import com.example.repository.TeacherRepository;
import com.example.service.AuthSessionService;
import com.example.service.RememberMeService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // Allowed origins for browser web + Capacitor native shells:
    //   http(s)://localhost(:*)   — dev web + Android (http://localhost scheme, no port)
    //   capacitor://localhost     — iOS WKWebView
    @Value("${app.cors.allowed-origins:http://localhost,http://localhost:5173,http://localhost:*,http://127.0.0.1:*,https://localhost,https://localhost:*,capacitor://localhost}")
    private String allowedOrigins;

    @Value("${app.security.csrf.enabled:true}")
    private boolean csrfEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           RememberMeService rememberMeService,
                                           AuthSessionService authSessionService,
                                           StudentRepository studentRepository,
                                           TeacherRepository teacherRepository) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/auth/csrf",
                    "/api/auth/student/login",
                    "/api/auth/teacher/login",
                    "/api/consents/**"
                ).permitAll()
                .requestMatchers("/api/admin/**").hasRole("ACADEMY_ADMIN")
                .requestMatchers("/api/**").authenticated()
                .requestMatchers("/actuator/**").hasAnyRole("TEACHER", "ACADEMY_ADMIN")
                .anyRequest().denyAll()
            )
            .addFilterBefore(new RememberMeAuthenticationFilter(
                            rememberMeService,
                            authSessionService,
                            studentRepository,
                            teacherRepository),
                    UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new SessionAuthenticationFilter(),
                    RememberMeAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    new ObjectMapper().writeValue(response.getWriter(),
                            Map.of("message", "로그인이 필요합니다."));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    new ObjectMapper().writeValue(response.getWriter(),
                            Map.of("message", "접근 권한이 없습니다."));
                })
            )
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        if (csrfEnabled) {
            http.csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));
        } else {
            http.csrf(AbstractHttpConfigurer::disable);
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList(parseAllowedOrigins()));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private String[] parseAllowedOrigins() {
        return allowedOrigins.split(",");
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        // ACADEMY_ADMIN and ASSISTANT both satisfy hasRole('TEACHER') for endpoint
        // authorization. Effective per-resource scoping (which data they can see,
        // which mutations they can perform) is enforced at the service layer via
        // Hibernate filters and AuthorizationService.assertNotAssistant().
        return RoleHierarchyImpl.fromHierarchy(
                "ROLE_ACADEMY_ADMIN > ROLE_TEACHER\n" +
                "ROLE_ASSISTANT > ROLE_TEACHER");
    }

    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
