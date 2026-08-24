package com.example.cinema_booking.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.cinema_booking.identity.application.port.in.RegisterCommand;
import com.example.cinema_booking.identity.application.port.in.RegisterResult;
import com.example.cinema_booking.identity.application.port.out.LoadUserPort;
import com.example.cinema_booking.identity.application.port.out.PasswordHasherPort;
import com.example.cinema_booking.identity.application.port.out.SaveUserPort;
import com.example.cinema_booking.identity.domain.User;
import com.example.cinema_booking.identity.domain.UserStatus;
import com.example.cinema_booking.identity.domain.exception.EmailAlreadyExistsException;
import com.example.cinema_booking.identity.domain.exception.PhoneAlreadyExistsException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterServiceTest {

  @Mock private LoadUserPort loadUserPort;
  @Mock private SaveUserPort saveUserPort;
  @Mock private PasswordHasherPort passwordHasherPort;

  private RegisterService registerService;

  @BeforeEach
  void setUp() {
    registerService = new RegisterService(loadUserPort, saveUserPort, passwordHasherPort);
  }

  /** Simulates the persistence adapter: same data, plus generated ids/timestamps. */
  private static User persisted(User draft) {
    return User.builder()
        .id(1L)
        .publicId(UUID.randomUUID())
        .email(draft.getEmail())
        .phone(draft.getPhone())
        .passwordHash(draft.getPasswordHash())
        .fullName(draft.getFullName())
        .dateOfBirth(draft.getDateOfBirth())
        .status(draft.getStatus())
        .build();
  }

  @Test
  void registerReturnsSavedUserInfoWithCustomerRole() {
    UUID publicId = UUID.randomUUID();
    when(loadUserPort.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);
    when(passwordHasherPort.encode("raw-password")).thenReturn("encoded-hash");
    when(saveUserPort.save(any(User.class), eq(RegisterService.CUSTOMER_ROLE_CODE)))
        .thenAnswer(
            invocation -> {
              User draft = invocation.getArgument(0);
              return User.builder()
                  .id(1L)
                  .publicId(publicId)
                  .email(draft.getEmail())
                  .phone(draft.getPhone())
                  .passwordHash(draft.getPasswordHash())
                  .fullName(draft.getFullName())
                  .dateOfBirth(draft.getDateOfBirth())
                  .status(draft.getStatus())
                  .build();
            });

    RegisterResult result =
        registerService.register(
            new RegisterCommand("Jane Doe", "jane@example.com", null, "raw-password", null));

    assertThat(result.publicId()).isEqualTo(publicId);
    assertThat(result.fullName()).isEqualTo("Jane Doe");
    assertThat(result.email()).isEqualTo("jane@example.com");
    assertThat(result.phone()).isNull();
    assertThat(result.roles()).containsExactly("CUSTOMER");

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(saveUserPort).save(userCaptor.capture(), eq(RegisterService.CUSTOMER_ROLE_CODE));
    User drafted = userCaptor.getValue();
    assertThat(drafted.getPasswordHash()).isEqualTo("encoded-hash");
    assertThat(drafted.getStatus()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  void registerTrimsEmailAndFullNameAndPhone() {
    when(loadUserPort.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);
    when(loadUserPort.existsByPhone("0912345678")).thenReturn(false);
    when(passwordHasherPort.encode(any())).thenReturn("encoded-hash");
    when(saveUserPort.save(any(User.class), any()))
        .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

    registerService.register(
        new RegisterCommand(
            "  Jane Doe  ", " jane@example.com ", " 0912345678 ", "raw-password", null));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(saveUserPort).save(captor.capture(), any());
    assertThat(captor.getValue().getEmail()).isEqualTo("jane@example.com");
    assertThat(captor.getValue().getFullName()).isEqualTo("Jane Doe");
    assertThat(captor.getValue().getPhone()).isEqualTo("0912345678");
  }

  @Test
  void registerNormalizesBlankPhoneToNull() {
    when(loadUserPort.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);
    when(passwordHasherPort.encode(any())).thenReturn("encoded-hash");
    when(saveUserPort.save(any(User.class), any()))
        .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

    registerService.register(
        new RegisterCommand("Jane Doe", "jane@example.com", "   ", "raw-password", null));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(saveUserPort).save(captor.capture(), any());
    assertThat(captor.getValue().getPhone()).isNull();
    verify(loadUserPort, never()).existsByPhone(any());
  }

  @Test
  void registerThrowsEmailAlreadyExistsWhenEmailIsTaken() {
    when(loadUserPort.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

    assertThatThrownBy(
            () ->
                registerService.register(
                    new RegisterCommand(
                        "Jane Doe", "taken@example.com", null, "raw-password", null)))
        .isInstanceOf(EmailAlreadyExistsException.class);

    verifyNoInteractions(saveUserPort, passwordHasherPort);
  }

  @Test
  void registerThrowsPhoneAlreadyExistsWhenPhoneIsTaken() {
    when(loadUserPort.existsByEmailIgnoreCase("jane@example.com")).thenReturn(false);
    when(loadUserPort.existsByPhone("0912345678")).thenReturn(true);

    assertThatThrownBy(
            () ->
                registerService.register(
                    new RegisterCommand(
                        "Jane Doe", "jane@example.com", "0912345678", "raw-password", null)))
        .isInstanceOf(PhoneAlreadyExistsException.class);

    verifyNoInteractions(saveUserPort, passwordHasherPort);
  }
}
