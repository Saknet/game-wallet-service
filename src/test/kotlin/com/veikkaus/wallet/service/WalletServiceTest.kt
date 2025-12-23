package com.veikkaus.wallet.service

import com.veikkaus.wallet.dto.TransactionRequest
import com.veikkaus.wallet.model.Player
import com.veikkaus.wallet.model.TransactionType
import com.veikkaus.wallet.model.WalletTransaction
import com.veikkaus.wallet.repository.PlayerRepository
import com.veikkaus.wallet.repository.TransactionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class WalletServiceTest {

    @Mock
    lateinit var playerRepository: PlayerRepository

    @Mock
    lateinit var transactionRepository: TransactionRepository

    @InjectMocks
    lateinit var walletService: WalletService

    private fun money(amount: Double) = BigDecimal.valueOf(amount).setScale(2)

    // --- DEBIT TESTS ---

    @Test
    fun `debit success (Happy Path)`() {
        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), money(10.0))
        val player = Player(req.playerId, "Test", money(100.0))

        // Use 'whenever' (Kotlin friendly) instead of 'when'
        whenever(transactionRepository.findById(req.transactionId)).thenReturn(Optional.empty())
        whenever(playerRepository.findByIdLocked(req.playerId)).thenReturn(player)

        val res = walletService.debit(req)

        assertEquals(money(90.0), res.balance)
        verify(playerRepository).save(player)
        // This 'any()' now comes from mockito-kotlin and handles null safety correctly
        verify(transactionRepository).save(any())
    }

    @Test
    fun `debit should throw InsufficientFundsException`() {
        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), money(100.0))
        val player = Player(req.playerId, "Test", money(50.0))

        whenever(transactionRepository.findById(req.transactionId)).thenReturn(Optional.empty())
        whenever(playerRepository.findByIdLocked(req.playerId)).thenReturn(player)

        assertThrows(InsufficientFundsException::class.java) {
            walletService.debit(req)
        }
    }

    @Test
    fun `debit should throw NoSuchElementException when player missing`() {
        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), money(10.0))
        whenever(transactionRepository.findById(req.transactionId)).thenReturn(Optional.empty())
        whenever(playerRepository.findByIdLocked(req.playerId)).thenReturn(null)

        assertThrows(NoSuchElementException::class.java) {
            walletService.debit(req)
        }
    }

    // --- CREDIT TESTS ---

    @Test
    fun `credit success (Happy Path)`() {
        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), money(20.0))
        val player = Player(req.playerId, "Test", money(100.0))

        whenever(transactionRepository.findById(req.transactionId)).thenReturn(Optional.empty())
        whenever(playerRepository.findByIdLocked(req.playerId)).thenReturn(player)

        val res = walletService.credit(req)

        assertEquals(money(120.0), res.balance)
        verify(playerRepository).save(player)
    }

    // --- IDEMPOTENCY TESTS ---

    @Test
    fun `debit idempotency success (Replay)`() {
        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), money(10.0))
        val existingTxn = WalletTransaction(
            req.transactionId, req.playerId, TransactionType.DEBIT, req.amount, money(90.0)
        )

        whenever(transactionRepository.findById(req.transactionId)).thenReturn(Optional.of(existingTxn))

        val res = walletService.debit(req)

        assertEquals(money(90.0), res.balance)
        // 'any()' here safely matches the non-null UUID argument
        verify(playerRepository, never()).findByIdLocked(any())
        verify(transactionRepository, never()).save(any())
    }

    @Test
    fun `idempotency fail - Amount Mismatch`() {
        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), money(10.0))
        val existingTxn = WalletTransaction(
            req.transactionId, req.playerId, TransactionType.DEBIT, money(50.0), money(50.0)
        )
        whenever(transactionRepository.findById(req.transactionId)).thenReturn(Optional.of(existingTxn))

        assertThrows(com.veikkaus.wallet.service.TransactionConflictException::class.java) { walletService.debit(req) }
    }

    @Test
    fun `idempotency fail - Player ID Mismatch`() {
        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), money(10.0))
        val existingTxn = WalletTransaction(
            req.transactionId, UUID.randomUUID(), TransactionType.DEBIT, req.amount, money(90.0)
        )
        whenever(transactionRepository.findById(req.transactionId)).thenReturn(Optional.of(existingTxn))

        assertThrows(com.veikkaus.wallet.service.TransactionConflictException::class.java) { walletService.debit(req) }
    }

    @Test
    fun `idempotency fail - Type Mismatch`() {
        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), money(10.0))
        // Request is DEBIT, but existing is CREDIT
        val existingTxn = WalletTransaction(
            req.transactionId, req.playerId, TransactionType.CREDIT, req.amount, money(110.0)
        )
        whenever(transactionRepository.findById(req.transactionId)).thenReturn(Optional.of(existingTxn))

        assertThrows(com.veikkaus.wallet.service.TransactionConflictException::class.java) { walletService.debit(req) }
    }

    @Test
    fun `credit should throw NoSuchElementException when player missing`() {
        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), money(20.0))
        whenever(transactionRepository.findById(req.transactionId)).thenReturn(Optional.empty())
        // Simulate player missing during CREDIT
        whenever(playerRepository.findByIdLocked(req.playerId)).thenReturn(null)

        assertThrows(NoSuchElementException::class.java) {
            walletService.credit(req)
        }
    }

    @Test
    fun `exception classes coverage`() {
        // Explicitly instantiate exceptions with null to cover the default parameter bytecode
        val ex1 = InsufficientFundsException()
        assertEquals(null, ex1.message)

        val ex2 = TransactionConflictException()
        assertEquals(null, ex2.message)
    }    
}