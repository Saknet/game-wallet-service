package com.veikkaus.wallet

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * The main entry point for the Game Wallet Service.
 *
 * ## Application Responsibilities
 * This class bootstraps the Spring Boot application context, automatically enabling:
 * - **Component Scanning:** Finds and registers [RestController], [Service], and [Repository] beans in sub-packages.
 * - **Auto-Configuration:** Configures PostgreSQL (JPA), HikariCP (Connection Pool), and Web MVC based on classpath dependencies.
 *
 * ## Operational Notes
 * - **Containerization:** When running in Docker, ensure the JDBC URL and credentials are passed as environment variables.
 */
@SpringBootApplication
class WalletApplication

/**
 * JVM Entry Point.
 *
 * Bootstraps the application, parsing command-line arguments and initializing the Spring Context.
 *
 * @param args Command line arguments passed to the application (e.g., `--server.port=9090` or `--debug`).
 */
fun main(args: Array<String>) {
    runApplication<WalletApplication>(*args)
}