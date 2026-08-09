package com.example.cinema_booking.identity.infrastructure.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin")
public record AdminSeedProperties(String email, String password, String fullName) {}
