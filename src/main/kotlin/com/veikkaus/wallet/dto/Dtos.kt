package com.veikkaus.wallet.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.util.UUID

/**
 * Represents a request to perform a wallet transaction (debit or credit).
 *
 * This DTO is used for both game purchases (debits) and winnings (credits).
 *
 * Validation is applied to ensure that:
 * - `transactionId` and `playerId` are not null.
 * - `amount` is positive and conforms to 2 decimal places (e.g., EUR).
 *
 * @property transactionId Globally unique identifier for this transaction (UUID).
 * @property playerId Identifier of the player whose wallet is affected (UUID).
 * @property amount The transaction amount, must be > 0, max 18 digits integer part, 2 decimal places.
 */
data class TransactionRequest(
    @field:NotNull
    val transactionId: UUID,

    @field:NotNull
    val playerId: UUID,

    @field:DecimalMin(value = "0.01", message = "Amount must be positive")
    @field:Digits(integer = 18, fraction = 2)
    val amount: BigDecimal
)

/**
 * Represents the response returned after processing a wallet transaction.
 *
 * Provides the resulting balance and references the original transaction and player.
 *
 * @property transactionId The unique transaction ID corresponding to this operation.
 * @property playerId The identifier of the player whose balance is returned.
 * @property balance Player's wallet balance after the transaction.
 */
data class BalanceResponse(
    val transactionId: UUID,
    val playerId: UUID,
    val balance: BigDecimal
)