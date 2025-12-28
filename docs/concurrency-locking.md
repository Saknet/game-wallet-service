# 1. Use Pessimistic Locking for Wallet Transactions

Date: 2025-12-23

## Context
We need to prevent race conditions when multiple threads try to debit the same wallet simultaneously. Optimistic locking (versioning) causes too many retry failures under high load.

## Decision
We will use `PESSIMISTIC_WRITE` (Select for Update) on the Player entity.

## Consequences
* **Positive:** Guarantees data integrity; prevents double spending completely.
* **Negative:** Slightly reduces throughput compared to optimistic locking, but acceptable for this use case.