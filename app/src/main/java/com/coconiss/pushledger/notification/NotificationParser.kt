package com.coconiss.pushledger.notification

import com.coconiss.pushledger.data.ParsedTransaction
import com.coconiss.pushledger.data.TransactionDirection
import com.coconiss.pushledger.data.TransactionRepository
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

object NotificationParser {
    private val amountRegex = Regex("""(?<!\d)(\d{1,3}(?:,\d{3})+|\d{3,})(?:\s*)(원|krw)?""", RegexOption.IGNORE_CASE)
    private val paymentWords = listOf("승인", "결제", "사용", "출금", "체크카드", "신용카드", "입금", "취소", "환불", "이체")

    private val ignoreWords = listOf(
        "인증번호",       // OTP/2FA 알림
        "로그인",         // 로그인 알림
        "광고",           // 광고성 알림
        "이벤트 당첨",    // 마케팅
        "적립 예정"       // 포인트 적립 예고 (결제 알림 아님)
    )

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

        // 정확한 ignoreWords 매칭: 단어 전체가 포함될 때만 무시
        if (ignoreWords.any { body.contains(it, ignoreCase = true) }) return null

        // paymentWords 중 하나라도 있어야 결제 알림으로 인정
        if (paymentWords.none { body.contains(it, ignoreCase = true) }) return null

        // 금액 추출: 100원 이상, 가장 큰 금액 (보통 결제금액이 잔액보다 앞에 있거나 더 작음)
        // 첫 번째로 나오는 100원 이상 금액을 결제금액으로 추정 (잔액보다 먼저 등장하는 경우가 많음)
        val amounts = amountRegex.findAll(body)
            .mapNotNull { match ->
                match.groupValues[1].replace(",", "").toLongOrNull()
            }
            .filter { it >= 100L }
            .toList()

        if (amounts.isEmpty()) return null

        // 결제금액은 보통 본문 앞부분에 등장, 잔액은 뒷부분
        // 첫 번째 금액을 사용하되, 두 금액 이상이면 더 작은 첫 금액을 선택 (잔액이 결제금액보다 큰 경우)
        val amount = if (amounts.size >= 2 && amounts[0] < amounts[1]) amounts[0] else amounts[0]

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
        val withoutAmount = original
            .replace(amount.toString(), "")
            .replace(String.format(Locale.KOREA, "%,d", amount), "")
        val tokens = withoutAmount
            .replace("[", " ").replace("]", " ")
            .replace("(", " ").replace(")", " ")
            .split(" ", "\n", "\t")
            .map { it.trim(',', '.', ':', '-', '/', '원') }
            .filter { it.length in 2..18 }
            .filterNot { token ->
                listOf("승인", "결제", "사용", "출금", "입금", "취소", "환불", "이체",
                    "잔액", "체크", "카드", "신용", "은행", "원").any { token.contains(it) }
            }
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