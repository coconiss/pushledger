package com.coconiss.pushledger.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class PushLedgerDatabase(context: Context) :
    SQLiteOpenHelper(context, "push_ledger.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                occurred_at INTEGER,
                captured_at INTEGER NOT NULL,
                amount INTEGER NOT NULL,
                direction TEXT NOT NULL,
                merchant_name TEXT,
                source_package TEXT NOT NULL,
                source_app_name TEXT,
                notification_key TEXT,
                fingerprint TEXT NOT NULL UNIQUE,
                category TEXT,
                category_confidence REAL NOT NULL,
                category_source TEXT NOT NULL,
                latitude REAL,
                longitude REAL,
                location_accuracy_m REAL,
                raw_text_preview TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_transactions_captured_at ON transactions(captured_at)")
        db.execSQL("CREATE INDEX idx_transactions_category ON transactions(category)")
        db.execSQL(
            """
            CREATE TABLE parse_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_package TEXT NOT NULL,
                posted_at INTEGER NOT NULL,
                parse_status TEXT NOT NULL,
                reason TEXT,
                transaction_id INTEGER
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future migrations belong here. Version 1 is the first public schema.
    }
}

class TransactionRepository(context: Context) {
    // SQLiteOpenHelper는 싱글턴처럼 유지해야 함. use { }로 매번 닫으면 안 됨.
    private val db = PushLedgerDatabase(context.applicationContext)

    @Synchronized
    fun insertIfNew(parsed: ParsedTransaction, location: LocationSnapshot?): InsertResult {
        val now = System.currentTimeMillis()
        val wdb = db.writableDatabase   // 닫지 않음 — Helper가 수명을 관리
        val existing = findIdByFingerprint(wdb, parsed.fingerprint)
        if (existing != null) {
            insertLog(wdb, parsed.sourcePackage, parsed.capturedAt, ParseStatus.DUPLICATE, "same fingerprint", existing)
            return InsertResult.Duplicate(existing)
        }

        val values = ContentValues().apply {
            put("occurred_at", parsed.occurredAt)
            put("captured_at", parsed.capturedAt)
            put("amount", parsed.amount)
            put("direction", parsed.direction.name)
            put("merchant_name", parsed.merchantName)
            put("source_package", parsed.sourcePackage)
            put("source_app_name", parsed.sourceAppName)
            put("notification_key", parsed.notificationKey)
            put("fingerprint", parsed.fingerprint)
            put("category", parsed.category)
            put("category_confidence", parsed.categoryConfidence)
            put("category_source", parsed.categorySource.name)
            put("latitude", location?.latitude)
            put("longitude", location?.longitude)
            put("location_accuracy_m", location?.accuracyM)
            put("raw_text_preview", parsed.rawTextPreview)
            put("created_at", now)
            put("updated_at", now)
        }
        val id = wdb.insertOrThrow("transactions", null, values)
        insertLog(wdb, parsed.sourcePackage, parsed.capturedAt, ParseStatus.CREATED, null, id)
        return InsertResult.Created(id)
    }

    @Synchronized
    fun updateCategory(id: Long, category: String, source: CategorySource = CategorySource.USER) {
        val values = ContentValues().apply {
            put("category", category)
            put("category_confidence", 1f)
            put("category_source", source.name)
            put("updated_at", System.currentTimeMillis())
        }
        db.writableDatabase.update("transactions", values, "id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun recent(limit: Int = 50): List<LedgerTransaction> =
        db.readableDatabase.query(
            "transactions", null, null, null, null, null,
            "captured_at DESC", limit.toString()
        ).use { cursor -> cursor.readTransactions() }

    @Synchronized
    fun between(startMillis: Long, endMillis: Long): List<LedgerTransaction> =
        db.readableDatabase.query(
            "transactions", null,
            "captured_at BETWEEN ? AND ?",
            arrayOf(startMillis.toString(), endMillis.toString()),
            null, null, "captured_at DESC"
        ).use { cursor -> cursor.readTransactions() }

    @Synchronized
    fun summary(startMillis: Long, endMillis: Long): LedgerSummary {
        val rows = between(startMillis, endMillis)
        val expense = rows.filter { it.direction == TransactionDirection.EXPENSE }.sumOf { it.amount }
        val income = rows.filter { it.direction == TransactionDirection.INCOME }.sumOf { it.amount }
        val uncategorized = rows.count { it.category.isNullOrBlank() || it.category == "미분류" }
        val categoryTotals = rows
            .filter { it.direction == TransactionDirection.EXPENSE }
            .groupBy { it.category ?: "미분류" }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
        return LedgerSummary(expense, income, uncategorized, categoryTotals)
    }

    @Synchronized
    fun deleteAll() {
        val wdb = db.writableDatabase
        wdb.delete("parse_logs", null, null)
        wdb.delete("transactions", null, null)
    }

    @Synchronized
    fun categoryHistoryForMerchant(merchant: String?): String? {
        if (merchant.isNullOrBlank()) return null
        return db.readableDatabase.rawQuery(
            """
            SELECT category, COUNT(*) AS cnt
            FROM transactions
            WHERE merchant_name = ? AND category IS NOT NULL
            GROUP BY category
            ORDER BY cnt DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(merchant)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun findIdByFingerprint(wdb: SQLiteDatabase, fingerprint: String): Long? =
        wdb.query(
            "transactions", arrayOf("id"),
            "fingerprint = ?", arrayOf(fingerprint),
            null, null, null, "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }

    private fun insertLog(
        wdb: SQLiteDatabase,
        sourcePackage: String,
        postedAt: Long,
        status: ParseStatus,
        reason: String?,
        transactionId: Long?
    ) {
        val values = ContentValues().apply {
            put("source_package", sourcePackage)
            put("posted_at", postedAt)
            put("parse_status", status.name)
            put("reason", reason)
            put("transaction_id", transactionId)
        }
        wdb.insert("parse_logs", null, values)
    }

    private fun Cursor.readTransactions(): List<LedgerTransaction> {
        val items = mutableListOf<LedgerTransaction>()
        while (moveToNext()) {
            items += LedgerTransaction(
                id = getLong(getColumnIndexOrThrow("id")),
                occurredAt = nullableLong("occurred_at"),
                capturedAt = getLong(getColumnIndexOrThrow("captured_at")),
                amount = getLong(getColumnIndexOrThrow("amount")),
                direction = enumValueOf(getString(getColumnIndexOrThrow("direction"))),
                merchantName = nullableString("merchant_name"),
                sourcePackage = getString(getColumnIndexOrThrow("source_package")),
                sourceAppName = nullableString("source_app_name"),
                notificationKey = nullableString("notification_key"),
                fingerprint = getString(getColumnIndexOrThrow("fingerprint")),
                category = nullableString("category"),
                categoryConfidence = getFloat(getColumnIndexOrThrow("category_confidence")),
                categorySource = enumValueOf(getString(getColumnIndexOrThrow("category_source"))),
                latitude = nullableDouble("latitude"),
                longitude = nullableDouble("longitude"),
                locationAccuracyM = nullableFloat("location_accuracy_m"),
                rawTextPreview = nullableString("raw_text_preview"),
                createdAt = getLong(getColumnIndexOrThrow("created_at")),
                updatedAt = getLong(getColumnIndexOrThrow("updated_at"))
            )
        }
        return items
    }

    private fun Cursor.nullableString(column: String): String? {
        val index = getColumnIndexOrThrow(column); return if (isNull(index)) null else getString(index)
    }
    private fun Cursor.nullableLong(column: String): Long? {
        val index = getColumnIndexOrThrow(column); return if (isNull(index)) null else getLong(index)
    }
    private fun Cursor.nullableDouble(column: String): Double? {
        val index = getColumnIndexOrThrow(column); return if (isNull(index)) null else getDouble(index)
    }
    private fun Cursor.nullableFloat(column: String): Float? {
        val index = getColumnIndexOrThrow(column); return if (isNull(index)) null else getFloat(index)
    }
}

sealed class InsertResult {
    data class Created(val id: Long) : InsertResult()
    data class Duplicate(val existingId: Long) : InsertResult()
}