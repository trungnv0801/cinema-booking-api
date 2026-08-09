package com.example.cinema_booking.identity.application.port.out;

import com.example.cinema_booking.identity.domain.User;
import java.util.List;

public interface TokenIssuerPort {

  IssuedTokens issue(User user, List<UserRoleAssignment> roles);
}
