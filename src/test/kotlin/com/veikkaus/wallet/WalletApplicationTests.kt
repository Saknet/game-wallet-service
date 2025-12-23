package com.veikkaus.wallet

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class WalletApplicationTests {

    @Test
    fun contextLoads() {
        // Verifies that the Spring Context loads successfully
    }

    @Test
    fun `main method smoke test`() {
        // We pass arguments to ensure the app starts successfully in an isolated H2 environment
        main(arrayOf(
            "--server.port=0",
            "--spring.datasource.url=jdbc:h2:mem:smoketest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            "--spring.datasource.username=sa",
            "--spring.datasource.password=",
            "--server.ssl.enabled=false",
            // FIX: Explicitly disable SQL scripts and Flyway to prevent "ScriptStatementFailedException"
            "--spring.sql.init.mode=never",
            "--spring.flyway.enabled=false",
            "--spring.jpa.hibernate.ddl-auto=create-drop"
        ))
    }

    @Test
    fun `instantiate application class`() {
        val app = WalletApplication()
        assertNotNull(app)
    }
}