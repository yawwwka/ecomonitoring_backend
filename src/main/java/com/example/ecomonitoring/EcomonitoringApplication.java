package com.example.ecomonitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EcomonitoringApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcomonitoringApplication.class, args);
    }
}