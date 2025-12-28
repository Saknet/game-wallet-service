package com.veikkaus.wallet.service

import com.veikkaus.wallet.dto.BalanceResponse
import com.veikkaus.wallet.dto.TransactionRequest
import com.veikkaus.wallet.model.TransactionType
import com.veikkaus.wallet.model.WalletTransaction
import com.veikkaus.wallet.repository.PlayerRepository
import com.veikkaus.wallet.repository.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Service responsible for the transactional management of player funds.
 *
 * This service implements the core business logic for the wallet system, ensuring
 * strictly consistent balance updates through database locking.
 *
 * ## Key Features
 * - **Concurrency Control:** Uses **Pessimistic Locking** (`SELECT ... FOR UPDATE`) to prevent race conditions.
 * See `docs/adr/001-concurrency-strategy.md` for architectural context.
 * - **Idempotency:** Guarantees that retrying the same [TransactionRequest.transactionId] yields the same result
 * without side effects.
 * - **Audit Trail:** All balance changes are recorded in the immutable [WalletTransaction] log.
 *
 * @property playerRepository Data access for mutable player state.
 * @property transactionRepository Data access for the immutable audit ledger.
 */
@Service
class WalletService(
    private val playerRepository: PlayerRepository,
    private val transactionRepository: TransactionRepository
) {

    private val logger = LoggerFactory.getLogger(WalletService::class.java)

    /**
     * Processes a Debit (Purchase) transaction.
     *
     * Validates sufficient funds before deducting the amount. This operation is atomic
     * and safe under high concurrency.
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
        // Includes a specific domain check for insufficient funds.
        return processTransaction(request, TransactionType.DEBIT) { currentBalance, amount ->
            if (currentBalance < amount) {
                logger.warn("Insufficient funds for player {} (Balance: {}, Req: {})",
                    request.playerId, currentBalance, amount)
                throw InsufficientFundsException("Player ${request.playerId} has insufficient funds")
            }
            currentBalance - amount
        }
    }

    /**
     * Processes a Credit (Win/Deposit) transaction.
     *
     * Adds the specified amount to the player's balance. This operation is atomic
     * and safe under high concurrency.
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
            currentBalance + amount
        }
    }

    /**
     * Unified transaction processor to handle locking, idempotency, and audit logging.
     *
     * This helper method ensures that the core transactional logic satisfies the **DRY principle**:
     * 1. **Idempotency Check:** Returns early if the transaction exists.
     * 2. **Locking:** Acquires a PESSIMISTIC_WRITE lock on the Player.
     * 3. **Calculation:** Applies the debit/credit strategy.
     * 4. **Persistence:** Saves the new player state.
     * 5. **Auditing:** Inserts the immutable transaction record.
     *
     * @param request The incoming API request.
     * @param type The type of transaction (DEBIT or CREDIT).
     * @param balanceCalculator A lambda function defining how to modify the balance.
     * @return The response with the final state.
     */
    private fun processTransaction(
        request: TransactionRequest,
        type: TransactionType,
        balanceCalculator: (BigDecimal, BigDecimal) -> BigDecimal
    ): BalanceResponse {

        // 1. Idempotency Check
        val existingTxn = transactionRepository.findByIdOrNull(request.transactionId)
        if (existingTxn != null) {
            logger.info("Idempotent replay detected for txnId={}", request.transactionId)
            validateIdempotency(existingTxn, request, type)

            return BalanceResponse(
                existingTxn.transactionId,
                existingTxn.playerId,
                existingTxn.balanceAfter
            )
        }

        // 2. Lock & Load Player
        // We use PESSIMISTIC_WRITE lock via the repository to serialize access.
        // This is the critical section for concurrency control.
        val player = playerRepository.findByIdLocked(request.playerId)
            ?: run {
                logger.error("Player {} not found during transaction", request.playerId)
                throw NoSuchElementException("Player ${request.playerId} not found")
            }

        // 3. Calculate New Balance
        val newBalance = balanceCalculator(player.balance, request.amount)

        // 4. Update State
        player.balance = newBalance
        playerRepository.save(player)

        // 5. Audit Log
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
     * This safeguards against clients reusing a Transaction ID for a *different* operation,
     * which indicates a logic error in the client rather than a network retry.
     *
     * @param existing The previously saved transaction.
     * @param request The current incoming request.
     * @param expectedType The expected operation type (DEBIT/CREDIT).
     * @throws TransactionConflictException If parameters (amount, player, or type) do not match.
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
 */
class InsufficientFundsException(message: String? = null) : RuntimeException(message)

/**
 * Exception thrown when a transaction ID already exists but parameters differ.
 */
class TransactionConflictException(message: String? = null) : RuntimeException(message)