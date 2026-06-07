package com.coconiss.pushledger.data

enum class TransactionDirection {
    EXPENSE,
    INCOME,
    TRANSFER,
    UNKNOWN
}

enum class CategorySource {
    USER,
    RULE,
    STATS,
    NONE
}

enum class ParseStatus {
    CREATED,
    DUPLICATE,
    IGNORED,
    FAILED
}

data class LedgerTransaction(
    val id: Long = 0,
    val occurredAt: Long?,
    val capturedAt: Long,
    val amount: Long,
    val direction: TransactionDirection,
    val merchantName: String?,
    val sourcePackage: String,
    val sourceAppName: String?,
    val notificationKey: String?,
    val fingerprint: String,
    val category: String?,
    val categoryConfidence: Float,
    val categorySource: CategorySource,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracyM: Float?,
    val rawTextPreview: String?,
    val createdAt: Long,
    val updatedAt: Long
)

data class ParsedTransaction(
    val occurredAt: Long?,
    val capturedAt: Long,
    val amount: Long,
    val direction: TransactionDirection,
    val merchantName: String?,
    val sourcePackage: String,
    val sourceAppName: String?,
    val notificationKey: String?,
    val fingerprint: String,
    val category: String?,
    val categoryConfidence: Float,
    val categorySource: CategorySource,
    val rawTextPreview: String?
)

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float?
)

data class LedgerSummary(
    val totalExpense: Long,
    val totalIncome: Long,
    val uncategorizedCount: Int,
    val categoryTotals: Map<String, Long>
)
