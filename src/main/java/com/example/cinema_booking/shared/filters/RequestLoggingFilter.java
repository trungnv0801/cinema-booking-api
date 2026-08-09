package com.example.cinema_booking.shared.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@Profile("prod")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

  private static final Set<String> SENSITIVE_KEYS =
      Set.of(
          "password",
          "confirmpassword",
          "newpassword",
          "oldpassword",
          "token",
          "accesstoken",
          "refreshtoken",
          "secret",
          "authorization",
          "otp",
          "cvv",
          "creditcard",
          "pin",
          "apikey");

  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");
  private static final String MASK = "***";
  private static final int MAX_BODY_LOG_LENGTH = 2000;
  private static final int MAX_CACHED_BODY_BYTES = 64 * 1024;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    ContentCachingRequestWrapper wrappedRequest =
        new ContentCachingRequestWrapper(request, MAX_CACHED_BODY_BYTES);
    long startTime = System.currentTimeMillis();
    try {
      filterChain.doFilter(wrappedRequest, response);
    } finally {
      long durationMs = System.currentTimeMillis() - startTime;
      log.info(
          "{} {} params=[{}] payload={} -> {} ({}ms) from {}",
          wrappedRequest.getMethod(),
          wrappedRequest.getRequestURI(),
          maskedParams(wrappedRequest),
          maskedBody(wrappedRequest),
          response.getStatus(),
          durationMs,
          wrappedRequest.getRemoteAddr());
    }
  }

  private String maskedParams(HttpServletRequest request) {
    Map<String, String[]> params = request.getParameterMap();
    if (params.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String[]> entry : params.entrySet()) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      String value = isSensitive(entry.getKey()) ? MASK : String.join(",", entry.getValue());
      sb.append(entry.getKey()).append('=').append(value);
    }
    return sb.toString();
  }

  private String maskedBody(ContentCachingRequestWrapper request) {
    byte[] content = request.getContentAsByteArray();
    if (content.length == 0) {
      return "";
    }
    String contentType = request.getContentType();
    if (contentType == null || !contentType.toLowerCase().contains("json")) {
      return "<%d bytes, non-JSON body omitted>".formatted(content.length);
    }
    try {
      JsonNode node = objectMapper.readTree(content);
      maskNode(node);
      String json = objectMapper.writeValueAsString(node);
      return json.length() > MAX_BODY_LOG_LENGTH
          ? json.substring(0, MAX_BODY_LOG_LENGTH) + "...(truncated)"
          : json;
    } catch (JacksonException e) {
      return "<unparseable body, %d bytes>".formatted(content.length);
    }
  }

  private void maskNode(JsonNode node) {
    if (node.isObject()) {
      ObjectNode objectNode = (ObjectNode) node;
      for (String fieldName : objectNode.propertyNames()) {
        if (isSensitive(fieldName)) {
          objectNode.put(fieldName, MASK);
        } else {
          maskNode(objectNode.get(fieldName));
        }
      }
    } else if (node.isArray()) {
      node.forEach(this::maskNode);
    }
  }

  private boolean isSensitive(String key) {
    String normalized = NON_ALPHANUMERIC.matcher(key.toLowerCase()).replaceAll("");
    return SENSITIVE_KEYS.contains(normalized);
  }
}
