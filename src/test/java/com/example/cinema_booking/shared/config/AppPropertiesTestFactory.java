package com.example.cinema_booking.shared.config;

import java.util.List;

public final class AppPropertiesTestFactory {

  private AppPropertiesTestFactory() {}

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private AppProperties.Cors cors = new AppProperties.Cors(List.of("http://localhost:3000"));
    private AppProperties.Api api = new AppProperties.Api("/api");
    private AppProperties.Server server = new AppProperties.Server("http://localhost:8080");
    private AppProperties.Jwt jwt = new AppProperties.Jwt("test-secret", 900, 30);
    private AppProperties.Cookie cookie = new AppProperties.Cookie(false);

    public Builder cors(AppProperties.Cors cors) {
      this.cors = cors;
      return this;
    }

    public Builder api(AppProperties.Api api) {
      this.api = api;
      return this;
    }

    public Builder server(AppProperties.Server server) {
      this.server = server;
      return this;
    }

    public Builder jwt(AppProperties.Jwt jwt) {
      this.jwt = jwt;
      return this;
    }

    public Builder cookie(AppProperties.Cookie cookie) {
      this.cookie = cookie;
      return this;
    }

    public AppProperties build() {
      return new AppProperties(cors, api, server, jwt, cookie);
    }
  }
}
