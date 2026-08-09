package com.example.cinema_booking.identity.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cinema_booking.identity.application.port.in.LoginCommand;
import com.example.cinema_booking.identity.application.port.in.LoginResult;
import com.example.cinema_booking.identity.application.port.in.LoginUseCase;
import com.example.cinema_booking.identity.domain.UserStatus;
import com.example.cinema_booking.identity.domain.exception.AccountNotActiveException;
import com.example.cinema_booking.identity.domain.exception.InvalidCredentialsException;
import com.example.cinema_booking.shared.config.AppProperties;
import com.example.cinema_booking.shared.config.AppPropertiesTestFactory;
import com.example.cinema_booking.shared.exception.GlobalExceptionHandler;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AuthControllerTest {

  private final LoginUseCase loginUseCase = mock(LoginUseCase.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    messageSource.setBasenames("messages");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);

    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(messageSource);
    validator.afterPropertiesSet();

    AppProperties appProperties =
        AppPropertiesTestFactory.builder().jwt(new AppProperties.Jwt("secret", 900, 7)).build();

    mockMvc =
        MockMvcBuilders.standaloneSetup(new AuthController(loginUseCase, appProperties))
            .setControllerAdvice(new GlobalExceptionHandler(messageSource))
            .setValidator(validator)
            .build();
  }

  @Test
  void loginReturnsTokensOnSuccess() throws Exception {
    UUID publicId = UUID.randomUUID();
    when(loginUseCase.login(any(LoginCommand.class)))
        .thenReturn(
            new LoginResult(
                "access-token",
                "refresh-token",
                "Bearer",
                900,
                publicId,
                "Jane Doe",
                List.of("CUSTOMER")));

    mockMvc
        .perform(
            post("/auth/login")
                .locale(Locale.ENGLISH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"identifier":"user@example.com","password":"correct-password"}
                    """))
        .andExpect(status().isOk())
        .andExpect(cookie().value("refreshToken", "refresh-token"))
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(900))
        .andExpect(jsonPath("$.user.id").value(publicId.toString()))
        .andExpect(jsonPath("$.user.fullName").value("Jane Doe"))
        .andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"));

    verify(loginUseCase).login(new LoginCommand("user@example.com", "correct-password"));
  }

  @Test
  void loginReturnsValidationErrorsWhenFieldsAreBlank() throws Exception {
    mockMvc
        .perform(
            post("/auth/login")
                .locale(Locale.ENGLISH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"identifier":"","password":""}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("The submitted data is invalid."))
        .andExpect(jsonPath("$.errors.identifier").value("Email or phone number is required."))
        .andExpect(jsonPath("$.errors.password").value("Password is required."));
  }

  @Test
  void loginReturnsUnauthorizedWhenCredentialsAreInvalid() throws Exception {
    when(loginUseCase.login(any(LoginCommand.class))).thenThrow(new InvalidCredentialsException());

    mockMvc
        .perform(
            post("/auth/login")
                .locale(Locale.ENGLISH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"identifier":"user@example.com","password":"wrong-password"}
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Invalid email/phone or password."));
  }

  @Test
  void loginReturnsForbiddenWhenAccountIsNotActive() throws Exception {
    when(loginUseCase.login(any(LoginCommand.class)))
        .thenThrow(new AccountNotActiveException(UserStatus.LOCKED));

    mockMvc
        .perform(
            post("/auth/login")
                .locale(Locale.ENGLISH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"identifier":"user@example.com","password":"correct-password"}
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Account is not active (LOCKED)."));
  }
}
