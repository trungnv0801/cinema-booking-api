package com.example.cinema_booking.shared.security;

import java.util.Optional;

public interface TokenAuthenticationPort {

  Optional<TokenPrincipal> authenticate(String token);
}
