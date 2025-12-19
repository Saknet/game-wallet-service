# Game Engine and Wallet HTTP API

## Overview

This document describes the HTTP API between the game engine and the wallet.  
The API allows the game engine to:

- debit a player's wallet (game purchase)
- credit winnings to a player's wallet

The API is **idempotent**, meaning that processing the same transaction multiple times does not result in multiple debits or credits.

All communication between the game engine and the wallet occurs over **encrypted HTTPS**.

---

## General Principles

- The API uses JSON for both requests and responses
- Amounts are represented as **strings** (e.g., "10.00") to strictly avoid IEEE 754 floating-point precision errors common in JSON number types.
- Each transaction has a globally unique transaction ID
- The wallet always returns the player’s balance **resulting from the processed transaction**

---

## Validation Rules

- Amount must be a positive decimal value greater than zero
- TransactionId must be a valid UUID
- PlayerId must be a valid UUID

## Security

- The API is only accessible via HTTPS
- Authentication and authorization (e.g., mTLS or API keys) are outside the scope of this exercise but would be considered in a production environment
- All requests are assumed to be authenticated and authorized by infrastructure-level security.

---

## Game Purchase (Debit)

The game engine debits the player's wallet for a game purchase.

### Endpoint

POST /api/wallet/debit

### Request body

| Field | Type | Description |
|-------|------|-------------|
| transactionId | string | Unique identifier for the purchase transaction (UUID) |
| playerId | string | Unique identifier for the player (UUID) |
| amount | string | Purchase amount (positive value, e.g., "5.00") |

#### Example

```json
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440001",
  "playerId": "123e4567-e89b-12d3-a456-426614174000",
  "amount": "5.00"
}
```

### Response (200 OK)

| Field | Type | Description |
|--------|--------|--------|
| transactionId | string | Echoes the transaction ID for confirmation |
| balance | string | Player's remaining wallet balance |

#### Example

```json
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440001",
  "balance": "95.00"
}
```

### Error Cases

#### Insufficient funds

HTTP 402 Payment Required

```json
{
  "error": "INSUFFICIENT_FUNDS",
  "message": "Player does not have enough funds."
}
```

### Idempotency

- Success replay: If the same transactionId is received multiple times with identical request parameters, the wallet returns the cached success response (200 OK) corresponding to the original transaction.
- Conflict: If the same transactionId is received with different parameters (e.g., different amount), the server returns 409 Conflict.

---

## Credit Winnings

The game engine credits the player's wallet with winnings.

### Endpoint

POST /api/wallet/credit

### Request body

| Field | Type | Description |
|--------|--------|--------|
| transactionId | string | Unique identifier for the credit transaction (UUID) |
| playerId | string | Unique identifier for the player (UUID) |
| amount | string | Winnings amount (positive value, e.g., "20.00") |

#### Example

```json
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440002",
  "playerId": "123e4567-e89b-12d3-a456-426614174000",
  "amount": "20.00"
}
```

### Response (200 OK)

| Field | Type | Description |
|--------|--------|--------|
| transactionId | string | Echoes the transaction ID |
| balance | string | Player's updated wallet balance |

#### Example

```json
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440002",
  "balance": "115.00"
}
```

### Idempotency

- Success Replay: If the same transactionId is received with the same parameters, the wallet returns the cached success response (200 OK) with the recorded balance after that transaction.
- Conflict: If the same transactionId is received with different parameters (e.g., different amount), the server returns 409 Conflict.

---

## HTTP Status Codes

| Status | Description |
|--------|--------|
| 200 OK | Transaction processed successfully |
| 400 Bad Request | Invalid input format or validation failure |
| 402 Payment Required | Player has insufficient funds |
| 404 Not Found | Player not found |
| 409 Conflict | Transaction ID already exists with different parameters |
| 500 Internal Server Error | Unexpected error |

---

## Summary

This API provides a simple and secure way to integrate the game engine and the wallet.
Idempotency ensures that the system can handle network retries and duplicate requests without double debits or credits.