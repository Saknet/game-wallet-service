package com.veikkaus.wallet.service

import com.veikkaus.wallet.dto.BalanceResponse
import com.veikkaus.wallet.dto.TransactionRequest
import com.veikkaus.wallet.model.TransactionType
import com.veikkaus.wallet.model.WalletTransaction
import com.veikkaus.wallet.repository.PlayerRepository
import com.veikkaus.wallet.repository.WalletTransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Service responsible for managing player wallet state.
 *
 * This service handles:
 * - **Concurrency Control:** Uses database-level pessimistic locking to prevent race conditions (double-spending).
 * - **Idempotency:** Ensures that retrying the same transaction ID yields the same result without side effects.
 * - **Audit Logging:** Persists an immutable record of every transaction.
 */
@Service
class WalletService(
    private val playerRepository: PlayerRepository,
    private val transactionRepository: WalletTransactionRepository
) {

    private val logger = LoggerFactory.getLogger(WalletService::class.java)

    /**
     * Processes a Debit (Purchase) transaction.
     *
     * Checks if the player has sufficient funds before deducting the amount.
     *
     * @param request The transaction details (ID, player, amount).
     * @return [BalanceResponse] containing the updated balance.
     * @throws InsufficientFundsException If the player's balance is less than the requested amount.
     * @throws TransactionConflictException If the transaction ID is reused with different parameters.
     * @throws NoSuchElementException If the player does not exist.
     */
    @Transactional
    fun debit(request: TransactionRequest): BalanceResponse {
        logger.info("Processing DEBIT [txnId={}, playerId={}, amount={}]",
            request.transactionId, request.playerId, request.amount)

        // Pass a lambda to calculate the new balance (Current - Amount)
        // Includes a specific check for insufficient funds.
        return processTransaction(request, TransactionType.DEBIT) { currentBalance, amount ->
            // Kotlin allows using '<' operator for BigDecimal
            if (currentBalance < amount) {
                logger.warn("Insufficient funds for player {} (Balance: {}, Req: {})",
                    request.playerId, currentBalance, amount)
                throw InsufficientFundsException("Player ${request.playerId} has insufficient funds")
            }
            // Kotlin allows using '-' operator for BigDecimal
            currentBalance - amount
        }
    }

    /**
     * Processes a Credit (Win) transaction.
     *
     * Adds the specified amount to the player's balance.
     *
     * @param request The transaction details (ID, player, amount).
     * @return [BalanceResponse] containing the updated balance.
     * @throws TransactionConflictException If the transaction ID is reused with different parameters.
     * @throws NoSuchElementException If the player does not exist.
     */
    @Transactional
    fun credit(request: TransactionRequest): BalanceResponse {
        logger.info("Processing CREDIT [txnId={}, playerId={}, amount={}]",
            request.transactionId, request.playerId, request.amount)

        // Pass a lambda to calculate the new balance (Current + Amount)
        return processTransaction(request, TransactionType.CREDIT) { currentBalance, amount ->
            // Kotlin allows using '+' operator for BigDecimal
            currentBalance + amount
        }
    }

    /**
     * Unified transaction processor to handle locking, idempotency, and audit logging.
     *
     * This helper method ensures that the core transactional logic (Lock -> Update -> Log)
     * is consistent across both Debits and Credits, satisfying the DRY principle.
     *
     * @param request The incoming API request.
     * @param type The type of transaction (DEBIT or CREDIT).
     * @param balanceCalculator A lambda function that defines how to calculate the new balance.
     * @return The response with the final state.
     */
    private fun processTransaction(
        request: TransactionRequest,
        type: TransactionType,
        balanceCalculator: (BigDecimal, BigDecimal) -> BigDecimal
    ): BalanceResponse {

        // 1. Idempotency Check
        // If this transaction ID has already been processed, we must return the
        // original result immediately to handle network retries gracefully.
        // Use findByIdOrNull (Kotlin extension for Spring Data)
        val existingTxn = transactionRepository.findByIdOrNull(request.transactionId)
        
        if (existingTxn != null) {
            logger.info("Idempotent replay detected for txnId={}", request.transactionId)

            // Verify that the retry parameters match the original transaction
            validateIdempotency(existingTxn, request, type)

            return BalanceResponse(
                existingTxn.transactionId,
                existingTxn.playerId,
                existingTxn.balanceAfter
            )
        }

        // 2. Lock & Load Player
        // We use PESSIMISTIC_WRITE lock (SELECT ... FOR UPDATE) to ensure that
        // no other thread can modify this player's balance until this transaction finishes.
        val player = playerRepository.findByIdLocked(request.playerId)
            ?: run {
                logger.error("Player {} not found during transaction", request.playerId)
                throw NoSuchElementException("Player ${request.playerId} not found")
            }

        // 3. Calculate New Balance
        // Execute the strategy provided by the caller (add or subtract)
        val newBalance = balanceCalculator(player.balance, request.amount)

        // 4. Update State
        player.balance = newBalance
        playerRepository.save(player)

        // 5. Audit Log
        // Record the immutable transaction history.
        val txn = WalletTransaction(
            transactionId = request.transactionId,
            playerId = player.id,
            type = type,
            amount = request.amount,
            balanceAfter = player.balance
        )
        transactionRepository.save(txn)

        logger.info("Transaction completed. New Balance for player {}: {}", player.id, player.balance)

        return BalanceResponse(txn.transactionId, player.id, player.balance)
    }

    /**
     * Validates that an existing transaction matches the current request parameters.
     *
     * If a client retries a transaction ID but changes the amount or player ID,
     * this is considered a conflict/error, not a valid replay.
     *
     * @param existing The previously saved transaction.
     * @param request The current incoming request.
     * @param expectedType The expected operation type (DEBIT/CREDIT).
     * @throws TransactionConflictException If parameters do not match.
     */
    private fun validateIdempotency(
        existing: WalletTransaction,
        request: TransactionRequest,
        expectedType: TransactionType
    ) {
        if (existing.playerId != request.playerId ||
            existing.amount.compareTo(request.amount) != 0 ||
            existing.type != expectedType
        ) {
            logger.warn("Idempotency conflict for txnId={}. Params mismatch.", request.transactionId)
            throw TransactionConflictException("Transaction ID ${request.transactionId} exists with different parameters")
        }
    }
}

/**
 * Exception thrown when a debit cannot be completed due to insufficient funds.
 * Note: 'message' is nullable (String?) to allow testing default error handling.
 */
class InsufficientFundsException(message: String? = null) : RuntimeException(message)

/**
 * Exception thrown when a transaction ID already exists but parameters differ.
 * Note: 'message' is nullable (String?) to allow testing default error handling.
 */
class TransactionConflictException(message: String? = null) : RuntimeException(message)