package com.example.cinema_booking.identity.infrastructure.persistence.repository;

import com.example.cinema_booking.identity.application.port.out.UserRoleAssignment;
import com.example.cinema_booking.identity.infrastructure.persistence.entity.UserRoleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRoleEntity, Long> {

  @Query(
      "select distinct new com.example.cinema_booking.identity.application.port.out"
          + ".UserRoleAssignment(ur.role.code, ur.cinemaId) "
          + "from UserRoleEntity ur where ur.user.id = :userId")
  List<UserRoleAssignment> findRolesByUserId(@Param("userId") Long userId);
}
