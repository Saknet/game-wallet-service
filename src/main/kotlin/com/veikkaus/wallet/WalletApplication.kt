package com.veikkaus.wallet

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Main Spring Boot application entry point for the Wallet Service.
 *
 * This class bootstraps the application context, enabling auto-configuration,
 * component scanning, and other Spring Boot features.
 */
@SpringBootApplication
class WalletApplication

fun main(args: Array<String>) {
    runApplication<WalletApplication>(*args)
}