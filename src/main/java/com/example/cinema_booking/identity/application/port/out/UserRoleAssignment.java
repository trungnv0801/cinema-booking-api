package com.example.cinema_booking.identity.application.port.out;

public record UserRoleAssignment(String roleCode, Long cinemaId) {

  public String toAuthority() {
    return cinemaId == null ? roleCode : roleCode + "_CINEMA_" + cinemaId;
  }
}
