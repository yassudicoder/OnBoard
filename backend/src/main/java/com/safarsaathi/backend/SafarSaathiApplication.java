package com.safarsaathi.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Starts the Safar Saathi backend.
 *
 * <p>{@code @SpringBootApplication} turns on Spring Boot's auto-configuration and makes Spring look through this
 * package and every sub-package for our classes ({@code @RestController}, {@code @Configuration}, {@code @Component}).
 * {@code @ConfigurationPropertiesScan} does the same for settings classes marked {@code @ConfigurationProperties},
 * such as {@link com.safarsaathi.backend.config.AppProperties} (the "safarsaathi.*" block in application.yml).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SafarSaathiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SafarSaathiApplication.class, args);
    }
}
