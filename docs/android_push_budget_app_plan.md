# Android push budget app plan

작성일: 2026-06-04

## 1. 제품 방향

이 앱은 사용자가 Android의 알림 접근 권한을 명시적으로 허용한 뒤, 은행/카드/간편결제/문자 앱에서 올라오는 결제성 푸쉬 알림을 분석해 로컬 가계부 항목을 자동 생성하는 앱이다. 데이터는 개인정보 보호를 위해 기기 안에만 저장하고, 선택 기능인 위치 기록과 앱 잠금은 사용자가 별도로 켜야 한다.

정책 리스크를 낮추기 위한 핵심 방향은 다음과 같다.

- `READ_SMS`, `RECEIVE_SMS`, `READ_CALL_LOG` 같은 SMS/통화기록 권한은 기본 설계에서 사용하지 않는다.
- 결제 정보 수집은 `NotificationListenerService` 기반으로 구현하고, 사용자가 시스템 설정에서 직접 켠 알림 접근 권한만 사용한다.
- 앱 자체 알림에 카테고리 액션 버튼을 제공하려면 Android 13 이상에서 `POST_NOTIFICATIONS` 런타임 권한을 요청한다. Android 표준 알림 액션은 최대 3개까지 제공한다.
- 위치 기록은 선택 기능이며, 항목 저장 시점에만 1회 위치를 읽는다. 백그라운드 위치 권한은 기본적으로 요청하지 않는다.
- 데이터 원문은 최소화한다. 파싱에 필요한 알림 제목/본문은 정규화된 거래 필드로 변환하고, 원문은 선택적으로 짧은 보관 기간을 둔다.

## 2. 기능 요구사항

| ID | 기능 | 구현 방향 | 필수/선택 |
| --- | --- | --- | --- |
| F01 | 은행/메시지 등의 푸쉬 알림을 읽어 자동 가계부 작성 | `NotificationListenerService.onNotificationPosted()`에서 허용된 앱 패키지의 알림만 파싱 | 필수 |
| F02 | 동일 결제건 중복 필터링 | 금액, 가맹점, 승인시각, 카드/계좌 식별자, 알림 발생시각 버킷을 조합한 지문으로 중복 제거 | 필수 |
| F03 | 알림창에서 바로 카테고리 입력 | 앱이 생성한 확인 알림에 추천 카테고리 액션 버튼 부착. 표준 알림 액션은 최대 3개이므로 상위 추천 2~3개와 `더보기` 진입을 제공 | 필수 |
| F04 | AI 또는 통계 기반 카테고리 추천 | 1차는 로컬 규칙/빈도 통계, 2차는 온디바이스 또는 사용자가 동의한 경우만 외부 AI | 선택 |
| F05 | 앱 실행 후 미분류 항목 분류 | 미분류 거래 목록, 빠른 필터, 다중 선택 카테고리 변경 제공 | 필수 |
| F06 | 저장 시점 GPS 저장 | 사용자가 켠 경우에만 항목 생성 시 현재 위치 1회 조회 | 선택 |
| F07 | 기간별 조회 및 Grid/차트 | 시작일-종료일 필터, 거래 Grid, 카테고리/월별 차트 | 필수 |
| F08 | 로컬 전용 저장 | Room DB + 암호화 스토리지 옵션, 네트워크 미사용 기본값 | 필수 |
| F09 | 앱 잠금 | PIN/비밀번호 + BiometricPrompt 지문/생체 인증 | 선택 |
| F10 | 다양한 화면 크기 대응 | Jetpack Compose adaptive layout, 작은 폰/큰 폰/태블릿 대응 | 필수 |
| F11 | 가계부다운 색감/디자인 | 차분한 재무 앱 톤, 수입/지출/경고 색상 체계, 과한 장식 지양 | 필수 |
| F12 | 권한 명시 및 확인 프로세스 | 온보딩에서 필수/선택 권한을 분리 설명하고, 설정 화면에서 상태 확인/해제 안내 | 필수 |

## 3. 권한 설계

### 필수 또는 핵심 권한

| 권한/접근 | 용도 | 요청 방식 | 비고 |
| --- | --- | --- | --- |
| Notification listener access | 결제성 알림 수신 및 파싱 | 앱 내 설명 후 `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`로 이동 | Android 특수 접근 권한. 일반 런타임 권한 대화상자가 아니다. |
| `POST_NOTIFICATIONS` | 앱 자체 분류 알림 표시 | Android 13+ 런타임 권한 | 카테고리 버튼 알림을 쓰려면 필요하다. |

### 선택 권한

| 권한/접근 | 용도 | 요청 방식 | 비고 |
| --- | --- | --- | --- |
| `ACCESS_COARSE_LOCATION` | 거래 저장 위치 대략 기록 | 런타임 권한 | 기본 추천. 개인정보와 배터리 부담이 낮다. |
| `ACCESS_FINE_LOCATION` | 거래 저장 위치 정밀 기록 | 런타임 권한 | 사용자가 정밀 위치를 원할 때만. |
| 생체 인증 사용 | 앱 잠금 해제 | `BiometricPrompt` | 생체정보 자체는 앱이 저장하지 않는다. |

### 사용하지 않을 권한

| 권한 | 제외 이유 |
| --- | --- |
| `READ_SMS`, `RECEIVE_SMS`, `WRITE_SMS` | Google Play 고위험/민감 권한 심사 대상이다. SMS 기반 가계부 예외 가능성은 있으나 승인 불확실성과 개인정보 리스크가 크다. |
| `READ_CALL_LOG`, `WRITE_CALL_LOG` | 본 앱 기능에 필요하지 않고 정책상 제한이 크다. |
| `ACCESS_BACKGROUND_LOCATION` | 저장 시점 1회 위치 조회만 필요하므로 백그라운드 위치 권한은 과하다. |
| `MANAGE_EXTERNAL_STORAGE` | 로컬 DB 저장에는 필요하지 않다. |
| `QUERY_ALL_PACKAGES` | 사용자가 직접 선택한 알림 발신 앱만 관리하면 충분하다. |

## 4. 정책 준수 체크리스트

- 앱 설명과 온보딩에서 “사용자가 허용한 알림에서 결제 정보를 읽어 로컬 가계부를 작성한다”는 핵심 기능을 명확히 고지한다.
- 알림 접근, 위치, 앱 잠금은 각각 별도 화면에서 목적, 수집 데이터, 저장 위치, 해제 방법을 설명한다.
- 개인정보처리방침 URL과 앱 내 개인정보처리방침 화면을 제공한다.
- Data Safety 섹션에는 금융/결제 정보, 위치 정보, 앱 활동 데이터 처리 여부를 실제 구현과 일치하게 기재한다.
- 기본값은 네트워크 미사용, 광고 SDK 미사용, 제3자 제공 없음으로 둔다.
- 외부 AI를 사용할 경우 별도 명시 동의, 전송 데이터 최소화, 비식별화, 끄기 기능이 필요하다. Play 심사와 개인정보처리방침에도 반영한다.
- 앱 자체 알림은 앱의 핵심 기능인 분류/확인 용도로만 사용하고, 광고나 타 서비스 홍보에 사용하지 않는다.

## 5. 데이터 모델 초안

```kotlin
data class Transaction(
    val id: Long,
    val occurredAt: Instant?,
    val capturedAt: Instant,
    val amount: Long,
    val currency: String = "KRW",
    val direction: TransactionDirection, // EXPENSE, INCOME, TRANSFER, UNKNOWN
    val merchantName: String?,
    val paymentMethodHint: String?,
    val sourcePackage: String,
    val sourceAppName: String?,
    val notificationKey: String?,
    val fingerprint: String,
    val categoryId: Long?,
    val categoryConfidence: Float?,
    val categorySource: CategorySource, // USER, RULE, STATS, AI, NONE
    val locationLat: Double?,
    val locationLng: Double?,
    val locationAccuracyM: Float?,
    val rawTextPreview: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

```kotlin
data class Category(
    val id: Long,
    val name: String, // 식비, 교통, 쇼핑, 생활, 의료, 기타 등
    val color: Long,
    val iconName: String,
    val sortOrder: Int
)
```

```kotlin
data class NotificationParseLog(
    val id: Long,
    val sourcePackage: String,
    val postedAt: Instant,
    val parseStatus: ParseStatus, // CREATED, DUPLICATE, IGNORED, FAILED
    val reason: String?,
    val transactionId: Long?
)
```

## 6. 중복 제거 전략

동일 결제건은 카드사, 은행, 문자 앱, 간편결제 앱에서 여러 번 알림으로 들어올 수 있다. 단순히 알림 ID만 보면 중복 제거가 되지 않으므로 거래 지문을 별도로 만든다.

1. 알림 텍스트 정규화
   - 금액 숫자 추출: `12,300원`, `KRW 12,300`, `출금 12300`
   - 가맹점 후보 추출: 승인/결제/사용 키워드 주변 문자열
   - 승인시각 후보 추출: 본문 내 시각, 없으면 알림 게시 시각
   - 카드/계좌 힌트 추출: `현대카드`, `국민`, `끝자리 1234` 등 비식별 수준

2. 지문 생성
   - 강한 지문: `amount + merchantNormalized + occurredAt(분 단위) + paymentMethodHint`
   - 약한 지문: `amount + merchantNormalized + capturedAt(5분 버킷)`
   - 같은 지문이 일정 시간창 안에 있으면 `DUPLICATE`로 처리

3. 충돌 방지
   - 같은 금액/가맹점이라도 5분 이상 차이가 나면 별도 거래로 본다.
   - 가맹점이 없는 알림은 중복 판정을 보수적으로 적용하고 미확인 상태로 둔다.
   - 사용자가 “중복 아님”으로 복구할 수 있는 화면을 제공한다.

## 7. 카테고리 추천

1차 추천은 로컬에서 처리한다.

- 사용자 규칙: `스타벅스 -> 식비`, `버스/지하철/택시 -> 교통`
- 빈도 통계: 같은 가맹점의 최근 사용자 분류를 우선 추천
- 키워드 사전: 음식점, 편의점, 주유, 대중교통, 온라인몰 등
- 신뢰도 낮음: 자동 확정하지 않고 “추천 카테고리”로만 표시

외부 AI는 기본값에서 제외한다. 개인 금융 데이터가 외부로 나갈 수 있으므로, 제품 초기 버전은 로컬 추천만으로 충분하다. AI가 필요하면 사용자가 명시적으로 켠 뒤 “가맹점명, 금액대, 날짜 일부, 기존 카테고리”처럼 최소 데이터만 보내는 방식으로 제한한다.

## 8. 화면 구성

### 온보딩

- 1단계: 앱 목적과 로컬 저장 원칙
- 2단계: 알림 접근 권한 설명 및 설정 이동
- 3단계: 앱 자체 알림 권한 요청
- 4단계: 선택 기능 설정
  - 위치 기록
  - 앱 잠금
  - 외부 AI 사용 여부

### 홈

- 오늘/이번 달 지출 요약
- 미분류 거래 수
- 최근 거래 목록
- 카테고리별 지출 막대 또는 도넛 차트

### 거래 목록

- 기간 선택: 시작일/종료일
- 필터: 전체, 미분류, 카테고리, 결제수단, 금액 범위
- Grid/List 전환
- 항목 클릭 시 상세/수정

### 분류함

- 미분류 거래를 빠르게 훑는 화면
- 카테고리 칩 버튼
- 동일 가맹점 일괄 적용 옵션

### 통계

- 카테고리별 지출
- 월별 추이
- 가맹점 TOP N
- 수입/지출/이체 구분

### 설정

- 권한 상태 확인
- 알림 수집 대상 앱 선택
- 위치 기록 on/off
- 앱 잠금 on/off
- 데이터 내보내기/삭제
- 개인정보처리방침

## 9. UI/UX 디자인 방향

- Jetpack Compose + Material 3 기반.
- 색상은 재무 앱 느낌의 차분한 기본 톤에 카테고리별 보조색을 쓴다.
- 예시 팔레트:
  - Background: `#F7F8F4`
  - Surface: `#FFFFFF`
  - Primary: `#2F6B5F`
  - Accent: `#D59E3D`
  - Expense: `#C85B4B`
  - Income: `#3E7C59`
  - Text: `#1F2723`
- 작은 화면에서는 하단 내비게이션, 넓은 화면에서는 내비게이션 레일과 리스트-상세 2열 구성을 사용한다.
- 금액, 날짜, 카테고리 칩은 한눈에 스캔되도록 간격과 대비를 높인다.
- 접근성: Dynamic Type, TalkBack 라벨, 충분한 터치 영역, 색상만으로 상태를 구분하지 않는 디자인을 적용한다.

## 10. 기술 아키텍처

권장 스택:

- Kotlin
- Jetpack Compose
- Material 3
- Room
- DataStore
- WorkManager
- Hilt 또는 Koin
- Kotlin Coroutines/Flow
- AndroidX Biometric
- Fused Location Provider 또는 Android location API

레이어:

- `app`: Compose UI, navigation, permission/onboarding
- `notification`: `NotificationListenerService`, notification parser, category action receiver
- `domain`: transaction use cases, duplicate detector, category recommender
- `data`: Room DAO, repository, DataStore settings
- `security`: app lock, encrypted settings, export/delete flow

## 11. 개발 로드맵

### MVP

- Compose 기본 앱 구조
- 온보딩 및 권한 상태 화면
- 알림 접근 권한 안내
- 결제 알림 파서 2~3개 샘플 지원
- Room 저장
- 중복 제거
- 미분류 목록 및 카테고리 수동 입력
- 기간별 목록/간단 차트
- 로컬 데이터 삭제

### v1

- 앱 자체 알림 액션 버튼. 예: `식비`, `교통`, `더보기` 또는 추천 상위 3개
- 카테고리 추천 규칙/통계
- 위치 기록 선택 기능
- 앱 잠금 선택 기능
- 데이터 내보내기 CSV
- 권한/개인정보처리방침/Play Data Safety 정리

### v1.5+

- 카드사/은행별 파서 확장
- 사용자가 직접 파싱 규칙 추가
- 차트 고도화
- 온디바이스 ML 또는 명시 동의 기반 AI 추천

## 12. 공식 참고 자료

- Google Play permissions declaration: https://support.google.com/googleplay/android-developer/answer/9214102
- Google Play user data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play SMS/Call Log permissions: https://support.google.com/googleplay/android-developer/answer/10208820
- Google Play sensitive permissions and APIs: https://support.google.com/googleplay/android-developer/answer/16558241
- Google Play system notification policy: https://support.google.com/googleplay/android-developer/answer/9969861
- Android `NotificationListenerService`: https://developer.android.com/reference/android/service/notification/NotificationListenerService
- Android notification permission: https://developer.android.com/develop/ui/views/notifications/notification-permission
- Android notification actions: https://developer.android.com/develop/ui/views/notifications/build-notification
- Android location permissions: https://developer.android.com/develop/sensors-and-location/location/permissions
- Android biometric authentication: https://developer.android.com/identity/sign-in/biometric-auth
- Compose adaptive UI: https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-screen-sizes
