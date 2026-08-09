package com.example.cinema_booking.identity.application.port.out;

import java.util.List;

public interface LoadUserRolesPort {

  List<UserRoleAssignment> loadRoles(Long userId);
}
