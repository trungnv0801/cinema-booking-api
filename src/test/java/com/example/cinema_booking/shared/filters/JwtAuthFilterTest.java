package com.example.cinema_booking.shared.filters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cinema_booking.shared.security.TokenAuthenticationPort;
import com.example.cinema_booking.shared.security.TokenPrincipal;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

  @Mock private TokenAuthenticationPort tokenAuthenticationPort;
  @Mock private FilterChain filterChain;

  private JwtAuthFilter filter;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    filter = new JwtAuthFilter(tokenAuthenticationPort);
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void setsAuthenticationWhenBearerTokenIsValid() throws Exception {
    request.addHeader("Authorization", "Bearer valid-token");
    when(tokenAuthenticationPort.authenticate("valid-token"))
        .thenReturn(
            Optional.of(new TokenPrincipal("user-public-id", List.of("ADMIN", "CUSTOMER"))));

    filter.doFilter(request, response, filterChain);

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    assertThat(authentication.getName()).isEqualTo("user-public-id");
    assertThat(authentication.getAuthorities())
        .extracting(Object::toString)
        .containsExactlyInAnyOrder("ADMIN", "CUSTOMER");
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void doesNotSetAuthenticationWhenTokenIsInvalid() throws Exception {
    request.addHeader("Authorization", "Bearer invalid-token");
    when(tokenAuthenticationPort.authenticate("invalid-token")).thenReturn(Optional.empty());

    filter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void setsEmptyAuthoritiesWhenRolesClaimIsMissing() throws Exception {
    request.addHeader("Authorization", "Bearer no-roles-token");
    when(tokenAuthenticationPort.authenticate("no-roles-token"))
        .thenReturn(Optional.of(new TokenPrincipal("user-public-id", List.of())));

    filter.doFilter(request, response, filterChain);

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    assertThat(authentication.getAuthorities()).isEmpty();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void doesNotSetAuthenticationWhenHeaderIsMissing() throws Exception {
    filter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(tokenAuthenticationPort, never()).authenticate(org.mockito.ArgumentMatchers.anyString());
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void doesNotSetAuthenticationWhenHeaderIsNotBearerScheme() throws Exception {
    request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

    filter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(tokenAuthenticationPort, never()).authenticate(org.mockito.ArgumentMatchers.anyString());
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void doesNotOverrideExistingAuthentication() throws Exception {
    request.addHeader("Authorization", "Bearer valid-token");
    Authentication existing =
        new UsernamePasswordAuthenticationToken("already-authenticated", null, List.of());
    SecurityContextHolder.getContext().setAuthentication(existing);

    filter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    verify(tokenAuthenticationPort, never()).authenticate(org.mockito.ArgumentMatchers.anyString());
  }
}
