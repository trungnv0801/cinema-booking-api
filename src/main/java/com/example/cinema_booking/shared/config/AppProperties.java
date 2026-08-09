package com.example.cinema_booking.shared.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Api api, Server server, Jwt jwt, Cookie cookie) {

  public record Cors(List<String> allowedOrigins) {}

  public record Api(String prefix) {}

  public record Server(String url) {}

  public record Jwt(String secret, long accessTokenTtlSeconds, long refreshTokenTtlDays) {}

  public record Cookie(Boolean secure) {}
}
