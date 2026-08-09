package com.example.cinema_booking.identity.application.port.in;

public record LoginCommand(String identifier, String rawPassword) {}
