package com.example.cinema_booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CinemaBookingApplication {

  public static void main(String[] args) {
    SpringApplication.run(CinemaBookingApplication.class, args);
  }
}
