package com.example.cinema_booking.shared.config;

import com.example.cinema_booking.shared.filters.JwtAuthFilter;
import com.example.cinema_booking.shared.security.RestAccessDeniedHandler;
import com.example.cinema_booking.shared.security.RestAuthenticationEntryPoint;
import com.example.cinema_booking.shared.security.TokenAuthenticationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final TokenAuthenticationPort tokenAuthenticationPort;
  private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
  private final RestAccessDeniedHandler restAccessDeniedHandler;
  private final String apiPrefix;

  public SecurityConfig(
      TokenAuthenticationPort tokenAuthenticationPort,
      RestAuthenticationEntryPoint restAuthenticationEntryPoint,
      RestAccessDeniedHandler restAccessDeniedHandler,
      AppProperties appProperties) {
    this.tokenAuthenticationPort = tokenAuthenticationPort;
    this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
    this.restAccessDeniedHandler = restAccessDeniedHandler;
    this.apiPrefix = appProperties.api().prefix();
  }

  @Bean
  public JwtAuthFilter jwtAuthFilter() {
    return new JwtAuthFilter(tokenAuthenticationPort);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
    http
        // Disable CSRF (not needed for stateless JWT)
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        // Stateless session (required for JWT)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // Disable HTTP Basic and form login: this API is JWT-only
        .httpBasic(httpBasic -> httpBasic.disable())
        .formLogin(formLogin -> formLogin.disable())
        // Configure endpoint authorization
        .authorizeHttpRequests(
            auth ->
                auth
                    // Public endpoints
                    .requestMatchers(
                        apiPrefix + "/auth/register",
                        apiPrefix + "/auth/login",
                        apiPrefix + "/auth/refresh",
                        apiPrefix + "/api-docs/**",
                        "/swagger-ui/**",
                        "/actuator/**")
                    .permitAll()

                    // Role-based endpoints
                    .requestMatchers(apiPrefix + "/auth/admin/**")
                    .hasAuthority("ADMIN")

                    // All other endpoints require authentication
                    .anyRequest()
                    .authenticated())

        // Return the same JSON error shape as GlobalExceptionHandler for failures that
        // never reach DispatcherServlet (rejected by the filter chain itself)
        .exceptionHandling(
            exceptionHandling ->
                exceptionHandling
                    .authenticationEntryPoint(restAuthenticationEntryPoint)
                    .accessDeniedHandler(restAccessDeniedHandler))

        // Add JWT filter before Spring Security's default filter
        .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  /*
   * Password encoder bean (uses BCrypt hashing)
   * Critical for secure password storage
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
