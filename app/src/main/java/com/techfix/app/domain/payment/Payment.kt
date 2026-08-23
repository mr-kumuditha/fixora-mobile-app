package com.techfix.app.domain.payment

/**
 * Simulated payment only — no real charge is ever made anywhere in this app
 * (see CLAUDE.md). The record exists so the receipt and repair history have
 * something real to read back.
 */
enum class PaymentMethod { CARD, CASH_ON_PICKUP }

enum class PaymentStatus { PENDING, SUCCESS, FAILED }

data class Payment(
    val id: String,
    val repairRequestId: String,
    val amount: Double,
    val method: PaymentMethod,
    val status: PaymentStatus,
    val receiptId: String,
    val createdAt: Long? = null,
)
