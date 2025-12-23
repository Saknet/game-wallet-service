package com.veikkaus.wallet.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.veikkaus.wallet.dto.BalanceResponse
import com.veikkaus.wallet.dto.TransactionRequest
import com.veikkaus.wallet.service.InsufficientFundsException
import com.veikkaus.wallet.service.TransactionConflictException
import com.veikkaus.wallet.service.WalletService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID
import java.util.NoSuchElementException

@WebMvcTest(WalletController::class)
@Import(GlobalExceptionHandler::class)
class WalletControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @MockBean lateinit var walletService: WalletService
    @Autowired lateinit var mapper: ObjectMapper

    @Test
    fun `debit should return 200 OK`() {
        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal("10.00"))
        val res = BalanceResponse(req.transactionId, req.playerId, BigDecimal("90.00"))
        `when`(walletService.debit(any())).thenReturn(res)

        mockMvc.perform(post("/api/v1/wallet/debit")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(req)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.balance").value(90.00))
    }

    @Test
    fun `credit should return 200 OK`() {
        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal("10.00"))
        val res = BalanceResponse(req.transactionId, req.playerId, BigDecimal("110.00"))
        `when`(walletService.credit(any())).thenReturn(res)

        mockMvc.perform(post("/api/v1/wallet/credit")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(req)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.balance").value(110.00))
    }

    @Test
    fun `should return 400 Bad Request for validation errors`() {
        val invalidJson = """
            {
                "transactionId": "${UUID.randomUUID()}",
                "playerId": "${UUID.randomUUID()}",
                "amount": -5.00
            }
        """.trimIndent()

        mockMvc.perform(post("/api/v1/wallet/debit")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidJson))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    @Test
    fun `should return 402 Payment Required`() {
        `when`(walletService.debit(any())).thenThrow(InsufficientFundsException("Funds low"))
        
        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal("10.00"))
        mockMvc.perform(post("/api/v1/wallet/debit")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(req)))
            .andExpect(status().isPaymentRequired)
            .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"))
    }

    @Test
    fun `should return 404 Not Found`() {
        `when`(walletService.debit(any())).thenThrow(NoSuchElementException("Player missing"))
        
        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal("10.00"))
        mockMvc.perform(post("/api/v1/wallet/debit")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(req)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("NOT_FOUND"))
    }

    @Test
    fun `should return 409 Conflict`() {
        `when`(walletService.credit(any())).thenThrow(TransactionConflictException("Mismatch"))

        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal("10.00"))
        mockMvc.perform(post("/api/v1/wallet/credit")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(req)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("CONFLICT"))
    }

    @Test
    fun `should return 500 Internal Error`() {
        `when`(walletService.debit(any())).thenThrow(RuntimeException("Boom"))

        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal("10.00"))
        mockMvc.perform(post("/api/v1/wallet/debit")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(req)))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error").value("INTERNAL_SERVER_ERROR"))
    }

    @Test
    fun `should return 500 with default message when exception message is null`() {
        `when`(walletService.debit(any())).thenThrow(RuntimeException())

        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal("10.00"))
        mockMvc.perform(post("/api/v1/wallet/debit")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(req)))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.message").value("Unexpected error"))
    }

    @Test
    fun `should handle InsufficientFundsException with null message`() {
        `when`(walletService.debit(any())).thenThrow(InsufficientFundsException(null))

        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal("10.00"))
        mockMvc.perform(post("/api/v1/wallet/debit")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(req)))
            .andExpect(status().isPaymentRequired)
            .andExpect(jsonPath("$.message").value(""))
    }

    @Test
    fun `should handle TransactionConflictException with null message`() {
        `when`(walletService.credit(any())).thenThrow(TransactionConflictException(null))

        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal("10.00"))
        mockMvc.perform(post("/api/v1/wallet/credit")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(req)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value(""))
    }

    @Test
    fun `should handle NoSuchElementException with null message`() {
        // FIX: Use no-arg constructor to guarantee null message without ambiguity
        `when`(walletService.debit(any())).thenThrow(NoSuchElementException())

        val req = TransactionRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal("10.00"))
        mockMvc.perform(post("/api/v1/wallet/debit")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(req)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value(""))
    }
}