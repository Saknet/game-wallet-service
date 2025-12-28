package com.veikkaus.wallet.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring Boot configuration for the OpenAPI (Swagger) documentation.
 *
 * This configuration creates the metadata definition for the API, which is then
 * used by SpringDoc to generate the interactive documentation.
 *
 * **Generated Endpoints:**
 * - **JSON Definition:** `/v3/api-docs`
 * - **Interactive UI:** `/swagger-ui.html`
 */
@Configuration
class OpenApiConfig {

    /**
     * Defines the global metadata for the Game Wallet Service API.
     *
     * Sets the API title, description, and version that appear in the Swagger UI header.
     *
     * @return A custom [OpenAPI] bean populated with service information.
     */
    @Bean
    fun walletOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(Info()
                .title("Game Wallet Service API")
                .description("Transactional wallet service for game engines. Supports debit/credit operations with pessimistic locking and idempotency.")
                .version("1.0.0"))
    }
}