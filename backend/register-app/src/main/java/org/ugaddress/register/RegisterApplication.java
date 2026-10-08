package org.ugaddress.register;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * National Address Register: a modular monolith.
 *
 * <p>Each direct sub-package is a Spring Modulith application module ({@code register}, {@code gazetteer},
 * {@code workflow}, {@code field}, {@code resolve}, {@code audit}, {@code ingest}, {@code shared}). Modules interact
 * only through the types in their top-level package or through application events.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RegisterApplication {

    /**
     * Starts the application.
     *
     * @param args command-line arguments
     */
    public static void main(final String[] args) {
        SpringApplication.run(RegisterApplication.class, args);
    }
}
