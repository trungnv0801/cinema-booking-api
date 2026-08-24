package com.example.cinema_booking.identity.application.port.out;

import com.example.cinema_booking.identity.domain.User;

public interface SaveUserPort {

  /**
   * Persists the user and grants {@code roleCode} in the same transaction. Returns the saved user
   * with generated id, public id and timestamps populated.
   */
  User save(User user, String roleCode);
}
