package com.cognilogistic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Bootstrap class for the CogniLogistic / Bhoomihaar Express Spring Boot application.
 *
 * <p>This is the single entry-point for the logistics order-management backend (BE-OM V3.6).
 * The platform is a multi-tenant SaaS used by Transport Provider (TP) accounts to create,
 * track, and manage freight orders through the lifecycle:
 * CREATED → ACKNOWLEDGED → FLEET_CONFIRMED → IN_TRANSIT → DELIVERED.
 *
 * <p>{@code @EnableJpaAuditing} activates automatic population of {@code created_at} and
 * {@code updated_at} fields on entities that extend {@link com.cognilogistic.platform.BaseEntity}.
 * {@code @EnableAsync} allows {@code @Async} methods (e.g., fire-and-forget notifications).
 *
 * <p>See {@code ARCHITECTURE.md} (in this same package) for a request-flow diagram and
 * a module-by-module map; see {@code GLOSSARY.md} for acronyms (TP, FTL, PTL, GR/LR,
 * Vahan, …) and the plain-English text of business rules BR-01 through BR-10.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableJpaAuditing
@EnableAsync
public class CogniLogisticApplication {

    /**
     * Application entry-point. Bootstraps the Spring context and starts the embedded Tomcat server.
     *
     * @param args command-line arguments passed to Spring Boot (e.g., {@code --spring.profiles.active=prod})
     */
    public static void main(String[] args) {
        SpringApplication.run(CogniLogisticApplication.class, args);
    }
}
