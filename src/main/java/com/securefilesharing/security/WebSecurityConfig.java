package com.securefilesharing.security;

import com.securefilesharing.security.jwt.AuthEntryPointJwt;
import com.securefilesharing.security.jwt.AuthTokenFilter;
import com.securefilesharing.security.services.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class WebSecurityConfig {
    @Autowired
    UserDetailsServiceImpl userDetailsService;

    @Autowired
    private AuthEntryPointJwt unauthorizedHandler;

    @Bean
    public AuthTokenFilter authenticationJwtTokenFilter() {
        return new AuthTokenFilter();
    }

    @Bean
    @SuppressWarnings("deprecation")
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();

        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());

        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public API endpoints
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/test/**").permitAll()

                // Auditor endpoints - READ-ONLY access for auditors, admins, super admins
                .requestMatchers("/api/audit/**").hasAnyRole("AUDITOR", "ADMIN", "SUPER_ADMIN")

                // Lock down admin APIs - Auditors should NOT have write access to admin functions
                .requestMatchers("/api/admin/**").hasAnyAuthority(
                    "PERM_ADMIN_ACCESS",
                    "ROLE_ADMIN",
                    "ROLE_SUPER_ADMIN")

                // Block auditors from file upload/download/delete operations
                .requestMatchers("/api/files/upload/**").hasAnyRole("USER", "ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/files/download/**").hasAnyRole("USER", "ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/files/delete/**").hasAnyRole("USER", "ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/files/share/**").hasAnyRole("USER", "ADMIN", "SUPER_ADMIN")

                // Static assets/pages (JWT is stored in localStorage, so HTML must be publicly retrievable)
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/login.html",
                    "/register.html",
                    "/dashboard.html",
                    "/files.html",
                    "/admin.html",
                    "/auditor.html",
                    "/js/**",
                    "/css/**",
                    "/favicon.ico")
                .permitAll()

                .anyRequest().authenticated());

        http.authenticationProvider(authenticationProvider());

        http.headers(headers -> headers
                .contentSecurityPolicy(csp -> csp
                .policyDirectives("script-src 'self' 'unsafe-inline'; object-src 'none'; base-uri 'self'")));

        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
