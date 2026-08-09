package com.example.cinema_booking.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private final MessageSource messageSource;

  public OpenApiConfig(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  @Bean
  public OpenAPI cinemaBookingOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title(message("openapi.title"))
                .description(message("openapi.description"))
                .version("v0"));
  }

  private String message(String code) {
    return messageSource.getMessage(code, null, Locale.ROOT);
  }
}
