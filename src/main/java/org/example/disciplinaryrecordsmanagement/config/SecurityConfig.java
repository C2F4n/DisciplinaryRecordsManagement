package org.example.disciplinaryrecordsmanagement.config;

import org.example.disciplinaryrecordsmanagement.security.AttemptLimiter;
import org.example.disciplinaryrecordsmanagement.security.SecurityResponseWriter;
import org.example.disciplinaryrecordsmanagement.security.SessionUser;
import org.example.disciplinaryrecordsmanagement.service.AccountService;
import jakarta.servlet.DispatcherType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfException;

import java.time.Clock;

import static org.example.disciplinaryrecordsmanagement.constant.ApiRoutes.*;
import static org.example.disciplinaryrecordsmanagement.exception.ErrorCode.*;

@Configuration
class SecurityConfig {
    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AccountService accounts) {
        return username -> accounts.credential(username)
                .map(account -> new SessionUser(account.username(), account.passwordHash(), account.passwordVersion()))
                .orElseThrow(() -> new UsernameNotFoundException(BAD_CREDENTIALS.message()));
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, SecurityResponseWriter responses, BusinessPolicy policy, Clock clock) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.svg").permitAll()
                        .requestMatchers(ROOT + CSRF, LOGIN, ROOT + BIND, ROOT + BIND_EMAIL_CODE, ROOT + BINDING_CLASSES).permitAll()
                        .requestMatchers(PATTERN).authenticated()
                        .anyRequest().denyAll())
                .formLogin(login -> login.loginPage("/").loginProcessingUrl(LOGIN)
                        .successHandler((req, res, auth) -> responses.success(res))
                        .failureHandler((req, res, error) -> {
                            if (error instanceof AuthenticationServiceException) {
                                LOG.error("Authentication service unavailable: type={}", error.getClass().getName());
                                responses.error(res, UNEXPECTED);
                            } else responses.error(res, BAD_CREDENTIALS);
                        }))
                .logout(logout -> logout.logoutUrl(LOGOUT)
                        .logoutSuccessHandler((req, res, auth) -> responses.success(res)))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, error) -> responses.error(res, UNAUTHENTICATED))
                        .accessDeniedHandler((req, res, error) -> responses.error(res,
                                error instanceof CsrfException ? INVALID_CSRF : ACCESS_DENIED)))
                .addFilterBefore(new AttemptLimiter(responses, policy, clock), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
