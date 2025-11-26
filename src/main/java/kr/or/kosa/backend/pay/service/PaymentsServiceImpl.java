package kr.or.kosa.backend.pay.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.or.kosa.backend.pay.dto.UpgradeQuoteResponse;
import kr.or.kosa.backend.pay.entity.Payments;
import kr.or.kosa.backend.pay.entity.Subscription;
import kr.or.kosa.backend.pay.entity.SubscriptionPlan;
import kr.or.kosa.backend.pay.repository.PaymentsMapper;
import kr.or.kosa.backend.pay.repository.SubscriptionMapper;
import kr.or.kosa.backend.pay.repository.SubscriptionPlanMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import java.util.UUID;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class PaymentsServiceImpl implements PaymentsService {

    private final PaymentsMapper paymentsMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final RestTemplate restTemplate;

    // 포인트 서비스
    private final PointService pointService;

    // 토스 응답 JSON 저장용
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SubscriptionPlanMapper subscriptionPlanMapper;

    /**
     * application.properties 의 toss.payments.key
     * - test_gsk_... 또는 test_sk_... 형태의 "시크릿 키" 여야 한다.
     */
    @Value("${toss.payments.key}")
    private String secretKey;

    public PaymentsServiceImpl(PaymentsMapper paymentsMapper,
                               SubscriptionMapper subscriptionMapper,
                               PointService pointService,
                               SubscriptionPlanMapper subscriptionPlanMapper) {
        this.paymentsMapper = paymentsMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.pointService = pointService;
        this.subscriptionPlanMapper = subscriptionPlanMapper;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 결제 준비 단계에서 DB에 기본 정보 저장
     * - 여기서 status, requestedAt 기본값 세팅
     * - originalAmount / usedPoint / amount 조합 검증
     * - 포인트 잔액 사전 검증까지 수행
     * - ★ 최근 2회 연속 환불 계정은 1개월 동안 결제 차단
     */
    @Override
    public Payments savePayment(Payments payments) {

        // 0-0) userId 필수
        String userId = payments.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("결제를 진행하려면 userId가 필요합니다.");
        }

        // ✅ orderId가 비어있으면 서버에서 생성
        if (payments.getOrderId() == null || payments.getOrderId().isBlank()) {
            String newOrderId = "ORD-" + System.currentTimeMillis()
                    + "-" + UUID.randomUUID().toString().substring(0, 8);
            payments.setOrderId(newOrderId);
        }

        // 0-1) 최근 2회 연속 환불 + 30일 이내 → 결제 차단
        if (isUserInRefundBan(userId)) {
            throw new IllegalArgumentException(
                    "최근 2회 연속 환불로 인해 1개월 동안 결제가 제한된 계정입니다.");
        }

        // 0-2) 기본 상태값
        payments.setStatus("READY");
        payments.setRequestedAt(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );

        int clientAmount   = payments.getAmount();          // 프론트에서 넘어온 최종 결제 금액
        int originalAmount = payments.getOriginalAmount();  // 플랜 원가
        int usedPoint      = payments.getUsedPoint();       // 사용 포인트

        // ✅ 0-3) 플랜 코드 기반 서버 정가 적용
        String planCode   = payments.getPlanCode();
        int serverPrice   = getMonthlyPrice(planCode); // 여기서 플랜 잘못되면 바로 튕김

        // BASIC / PRO 처럼 서버에 정가가 정의된 플랜이면 무조건 서버 금액으로 덮어쓴다
        if (serverPrice > 0) {
            originalAmount = serverPrice;
            payments.setOriginalAmount(serverPrice);
        }

        // 1) 기존 버전 호환:
        //    서버 정가가 없는 플랜(테스트용 등) + 포인트 미사용이면
        //    originalAmount <= 0 && usedPoint == 0 → amount 를 원가로 사용
        if (serverPrice <= 0 && originalAmount <= 0 && usedPoint == 0) {
            if (clientAmount <= 0) {
                throw new IllegalArgumentException("결제 금액이 올바르지 않습니다.");
            }
            originalAmount = clientAmount;
            payments.setOriginalAmount(originalAmount);
        }

        // 2) 범위 검증
        if (originalAmount <= 0) {
            throw new IllegalArgumentException("원래 결제 금액이 올바르지 않습니다.");
        }
        if (usedPoint < 0) {
            throw new IllegalArgumentException("사용 포인트는 0보다 작을 수 없습니다.");
        }
        if (usedPoint > originalAmount) {
            throw new IllegalArgumentException("사용 포인트가 결제 금액보다 클 수 없습니다.");
        }

        int expectedAmount = originalAmount - usedPoint;
        if (expectedAmount <= 0) {
            throw new IllegalArgumentException("최종 결제 금액이 0 이하가 될 수 없습니다.");
        }

        // 3) 최종 결제 금액 검증 (클라가 보내준 amount vs 서버 계산값)
        if (clientAmount != 0 && clientAmount != expectedAmount) {
            throw new IllegalArgumentException("요청된 결제 금액과 포인트 적용 금액이 일치하지 않습니다.");
        }

        // 서버 계산값으로 강제 세팅
        payments.setAmount(expectedAmount);

        // 4) 포인트 잔액 사전 검증
        if (usedPoint > 0) {
            pointService.validatePointBalance(userId, usedPoint);
        }

        // 5) DB 저장 (idempotent 처리 그대로)
        Optional<Payments> existingOpt = paymentsMapper.findPaymentByOrderId(payments.getOrderId());

        if (existingOpt.isEmpty()) {
            paymentsMapper.insertPayment(payments);
            return payments;
        }

        Payments existing = existingOpt.get();

        if ("DONE".equalsIgnoreCase(existing.getStatus())) {
            throw new IllegalStateException("이미 결제가 완료된 주문입니다. 새로운 결제는 다른 orderId를 사용해야 합니다.");
        }

        existing.setUserId(payments.getUserId());
        existing.setPlanCode(payments.getPlanCode());
        existing.setOrderName(payments.getOrderName());
        existing.setCustomerName(payments.getCustomerName());
        existing.setOriginalAmount(payments.getOriginalAmount());
        existing.setUsedPoint(payments.getUsedPoint());
        existing.setAmount(payments.getAmount());
        existing.setStatus(payments.getStatus());       // READY
        existing.setRequestedAt(payments.getRequestedAt());

        paymentsMapper.updatePaymentForReady(existing);

        return existing;
    }


    @Override
    public Optional<Payments> getPaymentByOrderId(String orderId) {
        return paymentsMapper.findPaymentByOrderId(orderId);
    }

    @Override
    public List<Subscription> getActiveSubscriptions(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }

        // 1) 먼저 만료된 ACTIVE 구독을 EXPIRED로 몰아서 정리
        subscriptionMapper.expireSubscriptionsByUserId(userId);

        // 2) 지금 시각 기준으로 여전히 ACTIVE + end_date > NOW() 인 것만 리턴
        return subscriptionMapper.findActiveSubscriptionsByUserId(userId);
    }

    /**
     * 토스 API 승인 요청 후 DB에 최종 반영 및 구독권 부여
     */
    @Override
    public Payments confirmAndSavePayment(String paymentKey, String orderId, Long amount) {

        // 1. DB에서 현재 결제 상태 조회
        Payments existingPayment = paymentsMapper.findPaymentByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 정보를 찾을 수 없습니다."));

        // 1-1. 이미 DONE 이면 중복 승인 방지
        if ("DONE".equals(existingPayment.getStatus())) {
            return existingPayment;
        }

        // 1-2. READY 가 아닌 다른 상태면 승인 불가
        if (!"READY".equals(existingPayment.getStatus())) {
            throw new IllegalStateException(
                    "결제 상태가 승인 가능한 상태가 아닙니다. 현재 상태: " + existingPayment.getStatus());
        }

        // 1-3. 서버에 저장된 금액과 요청 금액이 같은지 검증
        if (existingPayment.getAmount() != amount.intValue()) {
            throw new IllegalStateException("요청 금액이 서버에 저장된 금액과 일치하지 않습니다.");
        }

        // 2. 토스 승인 API 호출 준비
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(secretKey, "");

        Map<String, Object> body = new HashMap<>();
        body.put("paymentKey", paymentKey);
        body.put("orderId", orderId);
        body.put("amount", amount);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = "https://api.tosspayments.com/v1/payments/confirm";

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();

            if (response.getStatusCode() == HttpStatus.OK
                    && responseBody != null
                    && "DONE".equals(responseBody.get("status"))) {

                // 토스 method → 내부 코드로 변환
                String tossMethod = (String) responseBody.get("method");
                String internalPayMethod = convertTossMethodToInternal(tossMethod, responseBody);

                // 전체 응답 JSON 문자열로 저장
                String rawJson = toJsonString(responseBody);


                Map<String, Object> cardMap = null;
                Object cardObj = responseBody.get("card");
                if (cardObj instanceof Map<?, ?> m) {
                    // 제네릭 깨지니까 경고 무시하거나 캐스팅
                    cardMap = (Map<String, Object>) m;
                }

                String cardCompany = null;
                String approveNo   = null;
                String approvedAt  = null;

                if (cardMap != null) {
                    cardCompany = (String) cardMap.get("issuerCode");     // 또는 cardType, acquirerCode 등 토스 스펙 보고 결정
                    approveNo   = (String) cardMap.get("approveNo");      // 토스 응답 키명에 맞춰 수정
                }
                approvedAt = (String) responseBody.get("approvedAt");

                // ✅ 성공 로그
                System.out.println("[TOSS CONFIRM SUCCESS] orderId=" + orderId
                        + ", paymentKey=" + paymentKey
                        + ", method=" + internalPayMethod
                        + ", amount=" + amount
                        + ", cardCompany=" + cardCompany
                        + ", approveNo=" + approveNo);


                // ✅ 3. 결제 성공 시 DB 업데이트 (결제수단 + raw 응답 + 카드 정보 포함)
                Payments confirmedPayment = Payments.builder()
                        .paymentKey(paymentKey)
                        .orderId(orderId)
                        .status("DONE")
                        .payMethod(internalPayMethod)
                        .pgRawResponse(rawJson)
                        .cardCompany(cardCompany)
                        .cardApprovalNo(approveNo)
                        .approvedAt(approvedAt)
                        .build();

                paymentsMapper.updatePaymentStatus(confirmedPayment);

                // 3-1. 포인트 실제 차감
                String userId = existingPayment.getUserId();
                int usedPoint = existingPayment.getUsedPoint();
                if (userId != null && !userId.isEmpty() && usedPoint > 0) {
                    pointService.usePoint(userId, usedPoint, orderId);
                }

                // 4. 구독권 활성화
                grantSubscriptionToUser(orderId);

                // 5. 최종 상태 리턴
                return this.getPaymentByOrderId(orderId).orElseThrow(() ->
                        new IllegalStateException("승인되었으나 DB에서 최종 조회 실패"));

            } else {
                String errorMessage = (responseBody != null)
                        ? String.valueOf(responseBody.get("message"))
                        : "unknown error";
                throw new IllegalStateException("토스페이먼츠 승인 거부: " + errorMessage);
            }

        } catch (HttpClientErrorException e) {
            // ✅ 토스 쪽에서 400/401/404 등 에러 날 때 바디까지 로깅
            System.err.println("[TOSS CONFIRM ERROR] status=" + e.getStatusCode()
                    + ", body=" + e.getResponseBodyAsString());
            throw new IllegalStateException("토스페이먼츠 승인 거부: " + e.getResponseBodyAsString(), e);

        } catch (Exception e) {
            if (e instanceof IllegalStateException || e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new RuntimeException("결제 승인 중 알 수 없는 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 구독권 부여 로직
     */
    private void grantSubscriptionToUser(String orderId) {
        // 1) 결제 정보 조회
        Payments payment = paymentsMapper.findPaymentByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다."));

        // 2) 유저 식별 (userId 우선, 없으면 customerName fallback)
        String userId = payment.getUserId();
        if (userId == null || userId.isEmpty()) {
            userId = payment.getCustomerName();
        }

        // 3) 구독 타입 (planCode가 정석, 없으면 orderName 사용)
        String planCode = payment.getPlanCode();
        if (planCode == null || planCode.isEmpty()) {
            planCode = payment.getOrderName(); // 예: "Basic 구독권" 같은 문자열
        }

        LocalDateTime now = LocalDateTime.now();

        // 🔥 4) BASIC → PRO 업그레이드 처리
        if (userId != null
                && !userId.isEmpty()
                && "PRO".equalsIgnoreCase(planCode)) {

            Optional<Subscription> basicOpt =
                    subscriptionMapper.findLatestActiveSubscriptionByUserIdAndType(userId, "BASIC");

            if (basicOpt.isPresent()) {
                Subscription basicSub = basicOpt.get();

                LocalDateTime basicEnd = basicSub.getEndDate();

                if (basicEnd != null && basicEnd.isAfter(now)) {

                    subscriptionMapper.updateSubscriptionStatusToCanceled(
                            basicSub.getOrderId(),
                            "CANCELED"
                    );

                    Subscription proSubscription = Subscription.builder()
                            .userId(userId)
                            .orderId(orderId)
                            .subscriptionType("PRO")
                            .startDate(now)
                            .endDate(basicEnd) // BASIC 남은 기간만
                            .status("ACTIVE")
                            .build();

                    int inserted = subscriptionMapper.insertSubscription(proSubscription);
                    if (inserted != 1) {
                        throw new RuntimeException("구독권 업그레이드 정보 DB 저장 실패");
                    }

                    return;
                }
            }
        }

        // 5) 그 외 케이스는 기존처럼 "새 30일 구독" 생성
        LocalDateTime endDate = now.plusMonths(1);

        Subscription newSubscription = Subscription.builder()
                .userId(userId)
                .orderId(orderId)
                .subscriptionType(planCode)
                .startDate(now)
                .endDate(endDate)
                .status("ACTIVE")
                .build();

        int result = subscriptionMapper.insertSubscription(newSubscription);
        if (result != 1) {
            throw new RuntimeException("구독권 정보 DB 저장 실패");
        }
    }


    /**
     * 토스페이먼츠 환불/취소 처리
     *  - 결제 후 7일 이내만 환불 가능
     *  - 이미 CANCELED / DONE 이외 상태는 환불 불가
     */
    @Override
    @Transactional
    public Payments cancelPayment(String paymentKey, String cancelReason) {

        Payments paymentToCancel = paymentsMapper.findPaymentByPaymentKey(paymentKey)
                .orElseThrow(() ->
                        new IllegalArgumentException("취소할 결제 정보를 찾을 수 없습니다."));

        if ("CANCELED".equals(paymentToCancel.getStatus())) {
            throw new IllegalStateException("이미 취소된 결제입니다.");
        }

        if (!"DONE".equals(paymentToCancel.getStatus())) {
            throw new IllegalStateException(
                    "결제 완료 상태에서만 환불할 수 있습니다. (현재 상태: " + paymentToCancel.getStatus() + ")");
        }

        LocalDateTime requestedAt = parseDateTime(paymentToCancel.getRequestedAt());
        if (requestedAt != null && requestedAt.isBefore(LocalDateTime.now().minusDays(7))) {
            throw new IllegalArgumentException("결제 후 7일이 지난 건은 환불할 수 없습니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(secretKey, "");

        Map<String, Object> body = new HashMap<>();
        body.put("cancelReason", cancelReason);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = String.format("https://api.tosspayments.com/v1/payments/%s/cancel", paymentKey);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                String newStatus = (String) response.getBody().get("status");

                paymentsMapper.updatePaymentStatusToCanceled(paymentToCancel.getOrderId(), newStatus);
                subscriptionMapper.updateSubscriptionStatusToCanceled(paymentToCancel.getOrderId(), "CANCELED");

                String userId = paymentToCancel.getUserId();
                int usedPoint = paymentToCancel.getUsedPoint();
                if (userId != null && !userId.isEmpty() && usedPoint > 0) {
                    pointService.refundPoint(userId, usedPoint, paymentToCancel.getOrderId(), cancelReason);
                }

                return paymentsMapper.findPaymentByOrderId(paymentToCancel.getOrderId())
                        .orElse(paymentToCancel);

            } else {
                throw new IllegalStateException("토스페이먼츠 환불 요청 실패: HTTP " + response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("토스페이먼츠 환불 거부: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("환불 처리 중 알 수 없는 오류 발생", e);
        }
    }

    /**
     * 최근 2회 연속 환불 + 30일 이내인지 체크
     */
    private boolean isUserInRefundBan(String userId) {
        List<Payments> recent = paymentsMapper.findRecentPaymentsByUser(userId, 2);
        if (recent == null || recent.size() < 2) {
            return false;
        }

        Payments latest   = recent.get(0);
        Payments previous = recent.get(1);

        if (!"CANCELED".equals(latest.getStatus())
                || !"CANCELED".equals(previous.getStatus())) {
            return false;
        }

        LocalDateTime latestRequestedAt = parseDateTime(latest.getRequestedAt());
        if (latestRequestedAt == null) {
            return false;
        }

        return latestRequestedAt.isAfter(LocalDateTime.now().minusDays(30));
    }

    /**
     * DB/엔티티에서 가져온 날짜 문자열을 LocalDateTime으로 변환
     */
    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;

        try {
            if (value.contains("T")) {
                return LocalDateTime.parse(value);
            }

            String trimmed = value;
            if (value.length() >= 19) {
                trimmed = value.substring(0, 19); // "yyyy-MM-dd HH:mm:ss"
            }
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return LocalDateTime.parse(trimmed, fmt);
        } catch (Exception e) {
            return null;
        }
    }

    // ===== 토스 method → 내부 코드 매핑 =====
    private String convertTossMethodToInternal(String tossMethod, Map<String, Object> responseBody) {
        if (tossMethod == null) return "UNKNOWN";

        switch (tossMethod) {
            case "카드":
            case "CARD":
                return "CARD";

            case "계좌이체":
            case "ACCOUNT_TRANSFER":
                return "ACCOUNT_TRANSFER";

            case "휴대폰":
            case "MOBILE_PHONE":
                return "MOBILE_PHONE";

            case "가상계좌":
            case "VIRTUAL_ACCOUNT":
                return "VBANK";

            case "간편결제":
            case "EASY_PAY":
                // 간편결제일 때는 provider 까지 붙여서 저장 (EASY_KAKAOPAY 등)
                if (responseBody != null) {
                    Object easyPayObj = responseBody.get("easyPay");
                    if (easyPayObj instanceof Map<?, ?> easyMap) {
                        Object provider = easyMap.get("provider");
                        if (provider instanceof String p && !p.isBlank()) {
                            return "EASY_" + p.toUpperCase(); // EASY_KAKAOPAY / EASY_NAVERPAY ...
                        }
                    }
                }
                return "EASY_PAY";

            default:
                // 혹시 모를 값은 그냥 원본 문자열로 저장
                return tossMethod;
        }
    }

    // Map → JSON String (실패해도 결제 흐름은 깨지지 않게 null 리턴)
    private String toJsonString(Map<String, Object> map) {
        if (map == null) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    // 구독을 "한 달"로 볼 때 기준 일수
    private static final long SUBSCRIPTION_DAYS = 30L;

    // 플랜 월 요금
    private int getMonthlyPrice(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            return 0;
        }

        SubscriptionPlan plan =
                subscriptionPlanMapper.findActiveByPlanCode(planCode.toUpperCase());

        if (plan == null) {
            // 여기서 그냥 에러 내버리기
            throw new IllegalArgumentException("유효하지 않거나 비활성화된 구독 플랜입니다: " + planCode);
        }
        return plan.getMonthlyFee();
    }


    @Override
    public UpgradeQuoteResponse getUpgradeQuote(String userId, String targetPlanCode) {

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId는 필수입니다.");
        }
        if (targetPlanCode == null || targetPlanCode.isBlank()) {
            throw new IllegalArgumentException("planCode는 필수입니다.");
        }

        String normalizedTarget = targetPlanCode.toUpperCase();

        // 지금은 BASIC → PRO만 업그레이드로 취급
        if (!"PRO".equals(normalizedTarget)) {
            return UpgradeQuoteResponse.builder()
                    .upgrade(false)
                    .fromPlan(null)
                    .toPlan(normalizedTarget)
                    .usedDays(0)
                    .remainingDays(0)
                    .extraAmount(0)
                    .basicEndDate(null)
                    .build();
        }

        // 최신 ACTIVE BASIC 구독 1개 조회
        return subscriptionMapper.findLatestActiveSubscriptionByUserIdAndType(userId, "BASIC")
                .map(basicSub -> {

                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime start = basicSub.getStartDate();
                    LocalDateTime end = basicSub.getEndDate();

                    if (end == null || !end.isAfter(now)) {
                        return UpgradeQuoteResponse.builder()
                                .upgrade(false)
                                .fromPlan("BASIC")
                                .toPlan("PRO")
                                .usedDays(0)
                                .remainingDays(0)
                                .extraAmount(0)
                                .basicEndDate(null)
                                .build();
                    }

                    long totalDays = 0;
                    if (start != null && end != null) {
                        totalDays = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate());
                    }
                    if (totalDays <= 0) {
                        totalDays = SUBSCRIPTION_DAYS;
                    }

                    long usedDays = 0;
                    if (start != null) {
                        usedDays = ChronoUnit.DAYS.between(start.toLocalDate(), now.toLocalDate());
                        if (usedDays < 0) usedDays = 0;
                        if (usedDays > totalDays) usedDays = totalDays;
                    }

                    long remainingDays = totalDays - usedDays;
                    if (remainingDays <= 0) {
                        return UpgradeQuoteResponse.builder()
                                .upgrade(false)
                                .fromPlan("BASIC")
                                .toPlan("PRO")
                                .usedDays(usedDays)
                                .remainingDays(0)
                                .extraAmount(0)
                                .basicEndDate(null)
                                .build();
                    }

                    int basicPrice = getMonthlyPrice("BASIC");
                    int proPrice   = getMonthlyPrice("PRO");
                    int diff       = proPrice - basicPrice;

                    if (diff <= 0) {
                        return UpgradeQuoteResponse.builder()
                                .upgrade(false)
                                .fromPlan("BASIC")
                                .toPlan("PRO")
                                .usedDays(usedDays)
                                .remainingDays(remainingDays)
                                .extraAmount(0)
                                .basicEndDate(null)
                                .build();
                    }

                    double rawExtra = diff * (remainingDays / (double) totalDays);
                    int extraAmount = (int) Math.ceil(rawExtra);

                    String endStr = end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                    return UpgradeQuoteResponse.builder()
                            .upgrade(true)
                            .fromPlan("BASIC")
                            .toPlan("PRO")
                            .usedDays(usedDays)
                            .remainingDays(remainingDays)
                            .extraAmount(extraAmount)
                            .basicEndDate(endStr)
                            .build();
                })
                .orElseGet(() ->
                        UpgradeQuoteResponse.builder()
                                .upgrade(false)
                                .fromPlan(null)
                                .toPlan("PRO")
                                .usedDays(0)
                                .remainingDays(0)
                                .extraAmount(0)
                                .basicEndDate(null)
                                .build()
                );
    }
}
