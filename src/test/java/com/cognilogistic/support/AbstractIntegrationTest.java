package com.cognilogistic.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /**
     * Single MySQL container shared across the entire JVM (= the whole IT suite).
     * The static initializer block fires once per classloader, so the container starts
     * exactly once and lives for the lifetime of the JVM. Spring's @SpringBootTest
     * context cache then sees a stable JDBC URL across all IT classes.
     *
     * Why not @Container @Testcontainers: those rebuild per test class and break
     * Spring's context cache — Spring's first cached context keeps pointing at the
     * first container's port while subsequent test classes spin up new containers,
     * causing "connection refused" / HikariCP timeouts on every test after the first.
     *
     * @ServiceConnection auto-wires Spring's DataSource to this container.
     */
    @ServiceConnection
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("cognilogistic_dev_master_db")
            .withUsername("test")
            .withPassword("test")
            .withCommand("--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_0900_ai_ci",
                    "--default-time-zone=+00:00");

    static {
        MYSQL.start();
    }

    @Autowired
    protected MockMvc mockMvc;
}
