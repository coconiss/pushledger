package com.coconiss.pushledger.notification

import com.coconiss.pushledger.data.CategorySource
import com.coconiss.pushledger.data.TransactionRepository

data class CategoryRecommendation(
    val category: String,
    val confidence: Float,
    val source: CategorySource
)

object CategoryRecommender {
    private val rules = listOf(
        "식비" to listOf("카페", "커피", "스타벅스", "투썸", "이디야", "식당", "분식", "치킨", "피자", "버거", "김밥", "편의점"),
        "교통" to listOf("버스", "지하철", "택시", "카카오T", "주차", "하이패스", "철도", "코레일"),
        "쇼핑" to listOf("쿠팡", "네이버페이", "무신사", "올리브영", "마트", "백화점", "11번가", "G마켓", "SSG"),
        "생활" to listOf("관리비", "통신", "전기", "가스", "수도", "보험", "구독"),
        "의료" to listOf("병원", "약국", "의원", "치과")
    )

    fun recommend(merchant: String?, body: String, repository: TransactionRepository? = null): CategoryRecommendation {
        val historic = repository?.categoryHistoryForMerchant(merchant)
        if (!historic.isNullOrBlank()) {
            return CategoryRecommendation(historic, 0.95f, CategorySource.STATS)
        }

        val target = "${merchant.orEmpty()} $body".lowercase()
        for ((category, keywords) in rules) {
            if (keywords.any { target.contains(it.lowercase()) }) {
                return CategoryRecommendation(category, 0.84f, CategorySource.RULE)
            }
        }
        return CategoryRecommendation("미분류", 0.2f, CategorySource.NONE)
    }
}
