package com.example.cinema_booking.shared.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cinema_booking.shared.security.RestAccessDeniedHandler;
import com.example.cinema_booking.shared.security.RestAuthenticationEntryPoint;
import com.example.cinema_booking.shared.security.SecurityErrorResponseWriter;
import com.example.cinema_booking.shared.security.TokenAuthenticationPort;
import com.example.cinema_booking.shared.security.TokenPrincipal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(
    controllers = {
      SecurityFilterChainTest.ProbeController.class,
      SecurityFilterChainTest.UnprefixedProbeController.class
    })
@Import({
  WebConfig.class,
  SecurityConfig.class,
  CorsConfig.class,
  RestAuthenticationEntryPoint.class,
  RestAccessDeniedHandler.class,
  SecurityErrorResponseWriter.class,
  SecurityFilterChainTest.TestConfig.class,
  SecurityFilterChainTest.ProbeController.class,
  SecurityFilterChainTest.UnprefixedProbeController.class
})
class SecurityFilterChainTest {

  private static final String PREFIX = "/api/v1";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private TokenAuthenticationPort tokenAuthenticationPort;

  @ParameterizedTest
  @ValueSource(
      strings = {
        PREFIX + "/auth/register",
        PREFIX + "/auth/login",
        PREFIX + "/auth/refresh",
        PREFIX + "/api-docs/probe",
        "/swagger-ui/probe",
        "/actuator/probe"
      })
  void publicPathsArePermittedWithoutAuthentication(String path) throws Exception {
    mockMvc.perform(get(path)).andExpect(status().isOk());
  }

  @Test
  void adminOnlyPathRejectsRequestsWithNoToken() throws Exception {
    mockMvc
        .perform(get(PREFIX + "/auth/admin/probe"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void adminOnlyPathRejectsAuthenticatedNonAdminRole() throws Exception {
    when(tokenAuthenticationPort.authenticate("customer-token"))
        .thenReturn(Optional.of(new TokenPrincipal("user-1", List.of("CUSTOMER"))));

    mockMvc
        .perform(get(PREFIX + "/auth/admin/probe").header("Authorization", "Bearer customer-token"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void adminOnlyPathAllowsAdminRole() throws Exception {
    when(tokenAuthenticationPort.authenticate("admin-token"))
        .thenReturn(Optional.of(new TokenPrincipal("admin-1", List.of("ADMIN"))));

    mockMvc
        .perform(get(PREFIX + "/auth/admin/probe").header("Authorization", "Bearer admin-token"))
        .andExpect(status().isOk());
  }

  @Test
  void defaultRuleRejectsUnauthenticatedRequestsToAnyOtherPath() throws Exception {
    mockMvc
        .perform(get(PREFIX + "/some/protected/resource"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void defaultRuleAllowsAnyAuthenticatedRoleOnOtherPaths() throws Exception {
    when(tokenAuthenticationPort.authenticate("customer-token"))
        .thenReturn(Optional.of(new TokenPrincipal("user-1", List.of("CUSTOMER"))));

    mockMvc
        .perform(
            get(PREFIX + "/some/protected/resource")
                .header("Authorization", "Bearer customer-token"))
        .andExpect(status().isOk());
  }

  @Test
  void unrecognizedTokenIsTreatedAsUnauthenticated() throws Exception {
    when(tokenAuthenticationPort.authenticate("garbage")).thenReturn(Optional.empty());

    mockMvc
        .perform(get(PREFIX + "/some/protected/resource").header("Authorization", "Bearer garbage"))
        .andExpect(status().isUnauthorized());
  }

  /** Paths registered without the {@code app.api.prefix}; {@link WebConfig} adds it for us. */
  @RestController
  static class ProbeController {

    @GetMapping("/auth/register")
    String register() {
      return "ok";
    }

    @GetMapping("/auth/login")
    String login() {
      return "ok";
    }

    @GetMapping("/auth/refresh")
    String refresh() {
      return "ok";
    }

    @GetMapping("/api-docs/probe")
    String apiDocs() {
      return "ok";
    }

    @GetMapping("/auth/admin/probe")
    String adminOnly() {
      return "ok";
    }

    @GetMapping("/some/protected/resource")
    String protectedResource() {
      return "ok";
    }
  }

  /**
   * A plain {@code @Controller} (not {@code @RestController}) so {@link WebConfig}'s path-match
   * configurer leaves these mappings unprefixed, matching how springdoc/actuator's own real
   * endpoints are never routed through app controllers either.
   */
  @Controller
  static class UnprefixedProbeController {

    @GetMapping("/swagger-ui/probe")
    @ResponseBody
    String swaggerUi() {
      return "ok";
    }

    @GetMapping("/actuator/probe")
    @ResponseBody
    String actuator() {
      return "ok";
    }
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    AppProperties appProperties() {
      return new AppProperties(
          new AppProperties.Cors(List.of("http://localhost:5173")),
          new AppProperties.Api(PREFIX),
          new AppProperties.Server("http://localhost:8080"),
          new AppProperties.Jwt("test-secret", 900, 30),
          new AppProperties.Cookie(false));
    }

    @Bean
    MessageSource messageSource() {
      ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
      messageSource.setBasenames("messages");
      messageSource.setDefaultEncoding("UTF-8");
      messageSource.setFallbackToSystemLocale(false);
      return messageSource;
    }
  }
}
