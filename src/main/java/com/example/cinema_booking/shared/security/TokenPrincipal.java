package com.example.cinema_booking.shared.security;

import java.util.List;

public record TokenPrincipal(String subject, List<String> roles) {}
