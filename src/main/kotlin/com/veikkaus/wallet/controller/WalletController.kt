package com.veikkaus.wallet.controller

import com.veikkaus.wallet.dto.BalanceResponse
import com.veikkaus.wallet.dto.TransactionRequest
import com.veikkaus.wallet.service.InsufficientFundsException
import com.veikkaus.wallet.service.TransactionConflictException
import com.veikkaus.wallet.service.WalletService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST controller responsible for handling wallet-related HTTP endpoints.
 *
 * Provides APIs for debiting and crediting a player's wallet.
 *
 * @property walletService Service layer for wallet operations.
 */
@RestController
@RequestMapping("/api/v1/wallet")
class WalletController(private val walletService: WalletService) {

    /**
     * Debit a player's wallet (game purchase).
     *
     * Idempotent: if the same transactionId is submitted multiple times,
     * the original transaction result is returned.
     *
     * @param request Transaction details (playerId, transactionId, amount).
     * @return [BalanceResponse] with transactionId, playerId, and updated balance.
     */
    @PostMapping("/debit")
    fun debit(@Valid @RequestBody request: TransactionRequest): ResponseEntity<BalanceResponse> =
        ResponseEntity.ok(walletService.debit(request))

    /**
     * Credit a player's wallet (winnings).
     *
     * Idempotent: if the same transactionId is submitted multiple times,
     * the original transaction result is returned.
     *
     * @param request Transaction details (playerId, transactionId, amount).
     * @return [BalanceResponse] with transactionId, playerId, and updated balance.
     */
    @PostMapping("/credit")
    fun credit(@Valid @RequestBody request: TransactionRequest): ResponseEntity<BalanceResponse> =
        ResponseEntity.ok(walletService.credit(request))
}

/**
 * Global exception handler for wallet-related REST APIs.
 *
 * Catches and maps domain exceptions to appropriate HTTP status codes
 * with standardized JSON error response format.
 */
@ControllerAdvice
class GlobalExceptionHandler {

    /**
     * Handles validation errors (e.g. invalid JSON, missing fields, negative amounts).
     * This is required for @Valid to return 400 instead of 500.
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException::class)
    fun handleValidationErrors(e: org.springframework.web.bind.MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val errors = e.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to "BAD_REQUEST", "message" to errors))
    }

    /**
    * Handles cases where a player does not have sufficient funds.
    *
    * HTTP 402 (Payment Required) is intentionally used to represent a
    * domain-level payment failure where a monetary operation cannot be
    * completed due to insufficient balance.
    *
    * Although 402 is not commonly used in REST APIs, it accurately models
    * the semantics of a failed payment attempt in this wallet domain.
    *
    * @param e [InsufficientFundsException] thrown by the service layer.
    * @return HTTP 402 Payment Required with standardized error details.
    */
    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(e: InsufficientFundsException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
            .body(mapOf("error" to "INSUFFICIENT_FUNDS", "message" to (e.message ?: "")))

    /**
     * Handles transaction idempotency conflicts.
     *
     * @param e [TransactionConflictException] thrown when transactionId exists
     *          with different parameters.
     * @return HTTP 409 Conflict with error details.
     */
    @ExceptionHandler(TransactionConflictException::class)
    fun handleConflict(e: TransactionConflictException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(mapOf("error" to "CONFLICT", "message" to (e.message ?: "")))

    /**
     * Handles cases where a requested player does not exist.
     *
     * @param e [NoSuchElementException] thrown when playerId not found.
     * @return HTTP 404 Not Found with error details.
     */
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(e: NoSuchElementException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(mapOf("error" to "NOT_FOUND", "message" to (e.message ?: "")))

    /**
     * Handles any unanticipated exceptions.
     *
     * @param e [Exception] any uncaught exception.
     * @return HTTP 500 Internal Server Error with error details.
     */
    @ExceptionHandler(Exception::class)
    fun handleGeneric(e: Exception): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to "INTERNAL_SERVER_ERROR", "message" to (e.message ?: "Unexpected error")))
}