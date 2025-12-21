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
 * Repository for accessing Player entities.
 *
 * Responsibilities:
 * - Managing player persistence.
 * - Handling concurrency control via pessimistic locking.
 */
@Repository
interface PlayerRepository : JpaRepository<Player, UUID> {

    /**
     * Finds a player by ID and applies a **pessimistic write lock**.
     *
     * This uses `SELECT ... FOR UPDATE` at the database level. It ensures that
     * if multiple threads/instances try to modify the same player's balance
     * simultaneously, they are serialized (queued), preventing race conditions.
     *
     * @param id UUID of the player to retrieve.
     * @return The Player entity if found, or null.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Player p WHERE p.id = :id")
    fun findByIdLocked(id: UUID): Player?
}

/**
 * Repository for accessing WalletTransaction entities.
 *
 * Responsibilities:
 * - Persisting immutable transaction records (Audit Log).
 * - Retrieving transaction history for players.
 * - Providing primary key lookups for idempotency checks.
 */
@Repository
interface TransactionRepository : JpaRepository<WalletTransaction, UUID> {

    /**
     * Retrieves the transaction history for a specific player.
     *
     * Results are ordered by `createdAt` descending (newest first), which is
     * standard for displaying transaction history (e.g., "Last 10 transactions").
     *
     * @param playerId The UUID of the player.
     * @return List of transactions found for the player.
     */
    fun findAllByPlayerIdOrderByCreatedAtDesc(playerId: UUID): List<WalletTransaction>

}