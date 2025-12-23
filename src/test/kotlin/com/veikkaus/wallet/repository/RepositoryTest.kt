package com.veikkaus.wallet.repository

import com.veikkaus.wallet.model.Player
import com.veikkaus.wallet.model.TransactionType
import com.veikkaus.wallet.model.WalletTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.math.BigDecimal
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoryTest {

    @Autowired lateinit var playerRepository: PlayerRepository
    @Autowired lateinit var transactionRepository: TransactionRepository

    @Test
    fun `findByIdLocked should return player using custom query`() {
        val id = UUID.randomUUID()
        val player = Player(id, "Lock Test User", BigDecimal("100.00"))
        playerRepository.save(player)

        val found = playerRepository.findByIdLocked(id)
        
        assertNotNull(found)
        assertEquals(id, found?.id)
    }

    @Test
    fun `transaction history should be sorted by newest first`() {
        // 1. Create and Save the Player FIRST
        val playerId = UUID.randomUUID()
        val player = Player(playerId, "History User", BigDecimal("0.00"))
        playerRepository.save(player)

        // 2. Now save transactions linked to this valid player
        transactionRepository.save(createTxn(playerId))
        transactionRepository.save(createTxn(playerId))
        transactionRepository.save(createTxn(playerId))

        // 3. Retrieve and Verify
        val history = transactionRepository.findAllByPlayerIdOrderByCreatedAtDesc(playerId)

        assertEquals(3, history.size)
        assertTrue(history[0].createdAt >= history[1].createdAt, "Newest should be first")
        assertTrue(history[1].createdAt >= history[2].createdAt, "Oldest should be last")
    }

    private fun createTxn(playerId: UUID): WalletTransaction {
        return WalletTransaction(
            transactionId = UUID.randomUUID(),
            playerId = playerId,
            type = TransactionType.DEBIT,
            amount = BigDecimal.TEN,
            balanceAfter = BigDecimal.TEN
        )
    }
}