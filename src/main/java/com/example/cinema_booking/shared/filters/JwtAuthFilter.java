package com.example.cinema_booking.shared.filters;

import com.example.cinema_booking.shared.security.TokenAuthenticationPort;
import com.example.cinema_booking.shared.security.TokenPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthFilter extends OncePerRequestFilter {

  private final TokenAuthenticationPort tokenAuthenticationPort;

  public JwtAuthFilter(TokenAuthenticationPort tokenAuthenticationPort) {
    this.tokenAuthenticationPort = tokenAuthenticationPort;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authHeader = request.getHeader("Authorization");

    if (authHeader != null
        && authHeader.startsWith("Bearer ")
        && SecurityContextHolder.getContext().getAuthentication() == null) {
      String token = authHeader.substring(7);
      Optional<TokenPrincipal> principal = tokenAuthenticationPort.authenticate(token);

      if (principal.isPresent()) {
        List<GrantedAuthority> authorities =
            principal.get().roles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                .toList();

        UsernamePasswordAuthenticationToken authToken =
            new UsernamePasswordAuthenticationToken(principal.get().subject(), null, authorities);
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
      }
    }
    filterChain.doFilter(request, response);
  }
}
