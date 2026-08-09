package com.example.cinema_booking.identity.api;

import com.example.cinema_booking.identity.api.dto.LoginRequest;
import com.example.cinema_booking.identity.api.dto.LoginResponse;
import com.example.cinema_booking.identity.application.port.in.LoginCommand;
import com.example.cinema_booking.identity.application.port.in.LoginResult;
import com.example.cinema_booking.identity.application.port.in.LoginUseCase;
import com.example.cinema_booking.shared.config.AppProperties;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AuthController.BASE_PATH)
public class AuthController {

  static final String BASE_PATH = "/auth";

  private final LoginUseCase loginUseCase;
  private final AppProperties appProperties;

  public AuthController(LoginUseCase loginUseCase, AppProperties appProperties) {
    this.loginUseCase = loginUseCase;
    this.appProperties = appProperties;
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    LoginResult result =
        loginUseCase.login(new LoginCommand(request.identifier(), request.password()));

    ResponseCookie refreshTokenCookie =
        ResponseCookie.from("refreshToken", result.refreshToken())
            .httpOnly(true)
            .secure(appProperties.cookie().secure())
            .sameSite("Lax")
            .path(appProperties.api().prefix() + BASE_PATH)
            .maxAge(Duration.ofDays(appProperties.jwt().refreshTokenTtlDays()))
            .build();

    LoginResponse body =
        new LoginResponse(
            result.accessToken(),
            result.tokenType(),
            result.expiresInSeconds(),
            new LoginResponse.User(result.userPublicId(), result.fullName(), result.roles()));

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
        .body(body);
  }
}
