package com.veikkaus.wallet.repository

import com.veikkaus.wallet.model.Player
import com.veikkaus.wallet.model.WalletTransaction
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Repository for accessing and managing [Player] state.
 *
 * This repository is the critical point for **concurrency control**. By providing
 * locking mechanisms, it ensures that only one transaction can modify a player's
 * balance at a time.
 */
@Repository
interface PlayerRepository : JpaRepository<Player, UUID> {

    /**
     * Finds a player by ID and strictly acquires a **Pessimistic Write Lock** on the row.
     *
     * ## SQL Behavior
     * Executes `SELECT ... FOR UPDATE` (PostgreSQL).
     *
     * ## Blocking Behavior
     * If another transaction currently holds the lock for this [id], this method
     * will **block** (wait) until the lock is released. This serializes access
     * to the player's balance, preventing race conditions and double-spending.
     *
     * @param id The UUID of the player.
     * @return The [Player] entity if found, otherwise null.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Player p WHERE p.id = :id")
    fun findByIdLocked(id: UUID): Player?
}

/**
 * Repository for accessing [WalletTransaction] audit records.
 *
 * ## Responsibilities
 * 1. **Audit Log:** Persisting immutable records of every debit/credit.
 * 2. **Idempotency:** The inherited [existsById] method is used to check if a
 * transaction ID has already been processed.
 * 3. **History:** Retrieving past transactions for display.
 */
@Repository
interface WalletTransactionRepository : JpaRepository<WalletTransaction, UUID> {

    /**
     * Retrieves the transaction history for a specific player.
     *
     * **Ordering:** Results are ordered by [WalletTransaction.createdAt] descending
     * (newest transactions first), which is the standard requirement for UI history feeds.
     *
     * @param playerId The UUID of the player.
     * @return A list of transactions, or an empty list if none exist.
     */
    fun findAllByPlayerIdOrderByCreatedAtDesc(playerId: UUID): List<WalletTransaction>
}