package com.coconiss.pushledger.notification

import com.coconiss.pushledger.data.ParsedTransaction
import com.coconiss.pushledger.data.TransactionDirection
import com.coconiss.pushledger.data.TransactionRepository
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

object NotificationParser {
    private val amountRegex = Regex("""(?<!\d)(\d{1,3}(?:,\d{3})+|\d{3,})(?:\s*)(원|krw)?""", RegexOption.IGNORE_CASE)
    private val paymentWords = listOf("승인", "결제", "사용", "출금", "체크", "카드", "입금", "취소", "환불")
    private val ignoreWords = listOf("인증", "보안", "로그인", "잔액", "광고", "혜택", "이벤트", "적립 예정")

    fun parse(
        sourcePackage: String,
        sourceAppName: String?,
        notificationKey: String?,
        title: String?,
        text: String?,
        postedAt: Long,
        repository: TransactionRepository? = null
    ): ParsedTransaction? {
        val body = listOfNotNull(title, text).joinToString(" ").trim()
        if (body.isBlank()) return null
        if (ignoreWords.any { body.contains(it, ignoreCase = true) }) return null
        if (paymentWords.none { body.contains(it, ignoreCase = true) }) return null

        val amount = amountRegex.findAll(body)
            .mapNotNull { it.groupValues.firstOrNull()?.replace(",", "")?.toLongOrNull() }
            .filter { it >= 100L }
            .maxOrNull() ?: return null

        val direction = when {
            body.contains("입금") || body.contains("환불") || body.contains("취소") -> TransactionDirection.INCOME
            body.contains("이체") -> TransactionDirection.TRANSFER
            body.contains("승인") || body.contains("결제") || body.contains("사용") || body.contains("출금") -> TransactionDirection.EXPENSE
            else -> TransactionDirection.UNKNOWN
        }

        val merchant = guessMerchant(title, text, amount)
        val recommendation = CategoryRecommender.recommend(merchant, body, repository)
        val fingerprint = fingerprint(amount, merchant, postedAt, sourcePackage)

        return ParsedTransaction(
            occurredAt = postedAt,
            capturedAt = postedAt,
            amount = amount,
            direction = direction,
            merchantName = merchant,
            sourcePackage = sourcePackage,
            sourceAppName = sourceAppName,
            notificationKey = notificationKey,
            fingerprint = fingerprint,
            category = if (recommendation.confidence >= 0.8f) recommendation.category else null,
            categoryConfidence = recommendation.confidence,
            categorySource = recommendation.source,
            rawTextPreview = body.take(180)
        )
    }

    private fun guessMerchant(title: String?, text: String?, amount: Long): String? {
        val original = text?.takeIf { it.isNotBlank() } ?: title ?: return null
        val withoutAmount = original.replace(amount.toString(), "")
            .replace(String.format(Locale.KOREA, "%,d", amount), "")
        val tokens = withoutAmount  
            .replace("[", " ")
            .replace("]", " ")
            .replace("(", " ")
            .replace(")", " ")
            .split(" ", "\n", "\t")
            .map { it.trim(',', '.', ':', '-', '/', '원') }
            .filter { it.length in 2..18 }
            .filterNot { token -> paymentWords.any { token.contains(it) } }
            .filterNot { token -> token.any(Char::isDigit) && token.length < 6 }

        return tokens.lastOrNull()
            ?.replace(Regex("""[^가-힣a-zA-Z0-9&._ -]"""), "")
            ?.takeIf { it.isNotBlank() }
    }

    private fun fingerprint(amount: Long, merchant: String?, postedAt: Long, sourcePackage: String): String {
        val merchantKey = merchant
            ?.lowercase(Locale.KOREA)
            ?.replace(Regex("""\s+"""), "")
            ?.take(24)
            ?: "unknown"
        val bucket = postedAt / (5 * 60 * 1000)
        val basis = "$amount|$merchantKey|$bucket|${sourcePackage.substringAfterLast('.')}"
        return MessageDigest.getInstance("SHA-256")
            .digest(basis.toByteArray())
            .joinToString("") { "%02x".format(abs(it.toInt())) }
            .take(32)
    }
}
