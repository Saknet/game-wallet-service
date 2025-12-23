package com.veikkaus.wallet.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class WalletModelTests {

    @Test
    fun `Player data class methods`() {
        val id = UUID.randomUUID()
        val p1 = Player(id, "Name", BigDecimal.TEN)
        val p2 = Player(id, "Name", BigDecimal.TEN)
        val p3 = p1.copy(name = "Other")

        // 1. Identity Check
        assertEquals(p1, p1)

        // 2. Equality Check
        assertEquals(p1, p2)
        assertEquals(p1.hashCode(), p2.hashCode())

        // 3. Inequality Check
        assertNotEquals(p1, p3)

        // 4. Null and Type Checks
        assertFalse(p1.equals(null))
        assertFalse(p1.equals("Some String"))

        // 5. toString
        assertTrue(p1.toString().contains("Name"))

        // 6. Explicit Getter Calls (FIX for getName coverage)
        assertEquals(id, p1.id)
        assertEquals("Name", p1.name) // <--- This hits getName()
        assertEquals(BigDecimal.TEN, p1.balance)

        // 7. Destructuring
        val (i, n, b) = p1
        assertEquals(id, i)
        assertEquals("Name", n)
        assertEquals(BigDecimal.TEN, b)

        // 8. Mutability / Setter Check
        p1.balance = BigDecimal("99.99")
        assertEquals(BigDecimal("99.99"), p1.balance)
        
        // 9. Test Copy with the mutable field specifically
        val p4 = p1.copy(balance = BigDecimal.ZERO)
        assertEquals(BigDecimal.ZERO, p4.balance)
        assertNotEquals(p1, p4)
    }

    @Test
    fun `WalletTransaction data class methods`() {
        val id = UUID.randomUUID()
        val pid = UUID.randomUUID()
        val fixedTime = Instant.now()
        
        val t1 = WalletTransaction(id, pid, TransactionType.DEBIT, BigDecimal.TEN, BigDecimal.ONE, fixedTime)
        val t2 = WalletTransaction(id, pid, TransactionType.DEBIT, BigDecimal.TEN, BigDecimal.ONE, fixedTime)
        val t3 = t1.copy(amount = BigDecimal.ZERO)

        // 1. Identity & Equality
        assertEquals(t1, t1)
        assertEquals(t1, t2)
        assertEquals(t1.hashCode(), t2.hashCode())
        
        // 2. Inequality
        assertNotEquals(t1, t3)

        // 3. Null and Type Checks
        assertFalse(t1.equals(null))
        assertFalse(t1.equals(BigDecimal.TEN))

        // 4. toString
        assertTrue(t1.toString().contains("DEBIT"))
        
        // 5. Explicit Getters (Good practice for coverage)
        assertEquals(id, t1.transactionId)
        assertEquals(pid, t1.playerId)
        assertEquals(TransactionType.DEBIT, t1.type)
        
        // 6. Destructuring
        val (tid, _, _, _, _) = t1
        assertEquals(id, tid)
    }
}