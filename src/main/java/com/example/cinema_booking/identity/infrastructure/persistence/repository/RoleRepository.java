package com.example.cinema_booking.identity.infrastructure.persistence.repository;

import com.example.cinema_booking.identity.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, String> {}
