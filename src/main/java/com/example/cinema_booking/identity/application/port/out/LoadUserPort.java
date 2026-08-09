package com.example.cinema_booking.identity.application.port.out;

import com.example.cinema_booking.identity.domain.User;
import java.util.Optional;

public interface LoadUserPort {

  Optional<User> loadByIdentifier(String identifier);
}
