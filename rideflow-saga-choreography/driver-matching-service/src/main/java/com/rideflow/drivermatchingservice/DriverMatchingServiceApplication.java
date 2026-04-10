package com.rideflow.drivermatchingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DriverMatchingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DriverMatchingServiceApplication.class, args);
    }
}
