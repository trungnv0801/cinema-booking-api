package com.example.cinema_booking.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private final MessageSource messageSource;
  private final AppProperties appProperties;

  public OpenApiConfig(MessageSource messageSource, AppProperties appProperties) {
    this.messageSource = messageSource;
    this.appProperties = appProperties;
  }

  @Bean
  public OpenAPI cinemaBookingOpenApi() {
    Server customServer =
        new Server().url(appProperties.server().url()).description("Configured Environment Server");

    return new OpenAPI()
        .info(
            new Info()
                .title(message("openapi.title"))
                .description(message("openapi.description"))
                .version("v0"))
        .servers(List.of(customServer));
  }

  private String message(String code) {
    return messageSource.getMessage(code, null, Locale.ROOT);
  }
}
