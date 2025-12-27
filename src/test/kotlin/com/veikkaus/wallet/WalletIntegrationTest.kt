package com.veikkaus.wallet

import com.veikkaus.wallet.dto.TransactionRequest
import com.veikkaus.wallet.model.Player
import com.veikkaus.wallet.repository.PlayerRepository
import com.veikkaus.wallet.repository.TransactionRepository
import com.veikkaus.wallet.service.WalletService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.Collections

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WalletIntegrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:15")
            .apply {
                withDatabaseName("wallet_test")
                withUsername("test")
                withPassword("test")
            }

        @JvmStatic
        @DynamicPropertySource
        fun registerPgProperties(registry: DynamicPropertyRegistry) {
            // 1. Force Spring to use the Testcontainer's dynamic URL
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)

            // 2. IMPORTANT: Disable standard SQL init scripts to prevent conflicts
            registry.add("spring.sql.init.mode") { "never" }
            
            // 3. Let Hibernate create the schema based on our Entities
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
        }
    }

    @Autowired lateinit var walletService: WalletService
    @Autowired lateinit var playerRepository: PlayerRepository
    @Autowired lateinit var transactionRepository: TransactionRepository

    @BeforeEach
    fun setup() {
        // Clean up DB to ensure a fresh state for every test
        transactionRepository.deleteAll()
        playerRepository.deleteAll()
    }

    @Test
    fun `should handle concurrent debits correctly (Locking Test)`() {
        val playerId = UUID.randomUUID()
        playerRepository.save(Player(playerId, "Concurrent User", BigDecimal("100.00")))

        val threads = 10
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)
        val errors = Collections.synchronizedList(mutableListOf<Exception>())

        repeat(threads) {
            executor.submit {
                try {
                    walletService.debit(TransactionRequest(UUID.randomUUID(), playerId, BigDecimal("1.00")))
                } catch (e: Exception) {
                    errors.add(e)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        assertTrue(errors.isEmpty(), "Errors occurred during concurrent execution: $errors")

        val finalPlayer = playerRepository.findById(playerId).orElseThrow()
        assertEquals(0, BigDecimal("90.00").compareTo(finalPlayer.balance))
    }

    @Test
    fun `should handle idempotency (replay same request)`() {
        val playerId = UUID.randomUUID()
        playerRepository.save(Player(playerId, "Idempotent User", BigDecimal("50.00")))
        val txnId = UUID.randomUUID()

        val request = TransactionRequest(txnId, playerId, BigDecimal("10.00"))

        // 1. First execution
        val first = walletService.debit(request)
        assertEquals(0, BigDecimal("40.00").compareTo(first.balance))

        // 2. Replay (should return same result without error)
        val replay = walletService.debit(request)
        assertEquals(0, BigDecimal("40.00").compareTo(replay.balance))
    }

    @Test
    fun `should fail when player does not exist`() {
        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal("10.00"))

        assertThrows(NoSuchElementException::class.java) {
            walletService.debit(req)
        }
    }

    @Test
    fun `should fail when reusing transactionID with different parameters (Idempotency Conflict)`() {
        val playerId = UUID.randomUUID()
        playerRepository.save(Player(playerId, "Test User", BigDecimal("100.00")))
        val txnId = UUID.randomUUID()

        // 1. First successful transaction
        val req1 = TransactionRequest(txnId, playerId, BigDecimal("10.00"))
        walletService.debit(req1)

        // 2. Reuse ID but change amount (Should fail)
        val req2 = TransactionRequest(txnId, playerId, BigDecimal("500.00")) 
        
        assertThrows(com.veikkaus.wallet.service.TransactionConflictException::class.java) {
            walletService.debit(req2)
        }
    }
}