package com.example.cinema_booking.identity.infrastructure.persistence.repository;

import com.example.cinema_booking.identity.infrastructure.persistence.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
  Optional<UserEntity> findByEmailIgnoreCase(String email);

  Optional<UserEntity> findByPhone(String phone);
}
