package com.example.cinema_booking.identity.application;

import com.example.cinema_booking.identity.application.port.in.RegisterCommand;
import com.example.cinema_booking.identity.application.port.in.RegisterResult;
import com.example.cinema_booking.identity.application.port.in.RegisterUseCase;
import com.example.cinema_booking.identity.application.port.out.LoadUserPort;
import com.example.cinema_booking.identity.application.port.out.PasswordHasherPort;
import com.example.cinema_booking.identity.application.port.out.SaveUserPort;
import com.example.cinema_booking.identity.domain.User;
import com.example.cinema_booking.identity.domain.UserStatus;
import com.example.cinema_booking.identity.domain.exception.EmailAlreadyExistsException;
import com.example.cinema_booking.identity.domain.exception.PhoneAlreadyExistsException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RegisterService implements RegisterUseCase {

  static final String CUSTOMER_ROLE_CODE = "CUSTOMER";

  private final LoadUserPort loadUserPort;
  private final SaveUserPort saveUserPort;
  private final PasswordHasherPort passwordHasherPort;

  public RegisterService(
      LoadUserPort loadUserPort, SaveUserPort saveUserPort, PasswordHasherPort passwordHasherPort) {
    this.loadUserPort = loadUserPort;
    this.saveUserPort = saveUserPort;
    this.passwordHasherPort = passwordHasherPort;
  }

  @Override
  public RegisterResult register(RegisterCommand command) {
    String email = command.email().trim();
    String phone = StringUtils.hasText(command.phone()) ? command.phone().trim() : null;

    if (loadUserPort.existsByEmailIgnoreCase(email)) {
      throw new EmailAlreadyExistsException();
    }
    if (phone != null && loadUserPort.existsByPhone(phone)) {
      throw new PhoneAlreadyExistsException();
    }

    User newUser =
        User.builder()
            .email(email)
            .phone(phone)
            .passwordHash(passwordHasherPort.encode(command.rawPassword()))
            .fullName(command.fullName().trim())
            .dateOfBirth(command.dateOfBirth())
            .status(UserStatus.ACTIVE)
            .build();

    User savedUser = saveUserPort.save(newUser, CUSTOMER_ROLE_CODE);

    return new RegisterResult(
        savedUser.getPublicId(),
        savedUser.getFullName(),
        savedUser.getEmail(),
        savedUser.getPhone(),
        savedUser.getDateOfBirth(),
        List.of(CUSTOMER_ROLE_CODE));
  }
}
