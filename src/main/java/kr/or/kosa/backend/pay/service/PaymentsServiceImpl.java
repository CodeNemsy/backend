package kr.or.kosa.backend.pay.service;

import kr.or.kosa.backend.pay.dto.TossConfirmResult;
import kr.or.kosa.backend.pay.dto.UpgradeQuoteResponse;
import kr.or.kosa.backend.pay.entity.Payments;
import kr.or.kosa.backend.pay.entity.Subscription;
import kr.or.kosa.backend.pay.repository.PaymentsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentsServiceImpl implements PaymentsService {

    private final PaymentsMapper paymentsMapper;
    private final PointService pointService;
    private final TossPaymentsClient tossPaymentsClient;
    private final SubscriptionDomainService subscriptionDomainService;

    public PaymentsServiceImpl(PaymentsMapper paymentsMapper,
                               PointService pointService,
                               TossPaymentsClient tossPaymentsClient,
                               SubscriptionDomainService subscriptionDomainService) {
        this.paymentsMapper = paymentsMapper;
        this.pointService = pointService;
        this.tossPaymentsClient = tossPaymentsClient;
        this.subscriptionDomainService = subscriptionDomainService;
    }

    /**
     * READY ???+ (?ъ씤???꾩븸 寃곗젣?쇰㈃) 利됱떆 DONE 泥섎━
     */
    @Override
    @Transactional
    public Payments savePayment(Payments payments) {

        // 0) ?좎?/二쇰Ц 湲곕낯 寃利?+ orderId ?명똿
        String userId = validateUserAndInitOrderId(payments);

        // 理쒓렐 ?섎텋 ?대젰???곕Ⅸ 李⑤떒
        if (isUserInRefundBan(userId)) {
            throw new IllegalArgumentException(
                    "理쒓렐 2???곗냽 ?섎텋濡??명빐 1媛쒖썡 ?숈븞 寃곗젣媛 ?쒗븳??怨꾩젙?낅땲??");
        }

        // 1) 湲덉븸/?ъ씤???뺢퇋??+ ?좏슚??寃??+ ?ъ씤???붿븸 寃利?
        boolean pointOnly = normalizeAmountsAndValidate(payments, userId);

        // 2) DB ?? (idempotent)
        Payments persisted = upsertPayment(payments);

        // ??? ?? ??? ???? ?? ??
        if (pointOnly) {
            handlePointOnlyFlow(userId, persisted);
        }

        return persisted;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payments> getPaymentByOrderId(String orderId) {
        return paymentsMapper.findPaymentByOrderId(orderId);
    }

    @Override
    @Transactional
    public List<Subscription> getActiveSubscriptions(String userId) {
        return subscriptionDomainService.getActiveSubscriptions(userId);
    }

    @Override
    @Transactional
    public Payments confirmAndSavePayment(String paymentKey, String orderId, Long amount) {

        Payments existingPayment = paymentsMapper.findPaymentByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("二쇰Ц ?뺣낫瑜?李얠쓣 ???놁뒿?덈떎."));

        // ?대? DONE?대㈃ 洹몃?濡?諛섑솚 (硫깅벑??
        if ("DONE".equals(existingPayment.getStatus())) {
            return existingPayment;
        }

        if (!"READY".equals(existingPayment.getStatus())) {
            throw new IllegalStateException(
                    "寃곗젣 ?곹깭媛 ?뱀씤 媛?ν븳 ?곹깭媛 ?꾨떃?덈떎. ?꾩옱 ?곹깭: " + existingPayment.getStatus());
        }

        // BigDecimal ??Long 鍮꾧탳
        BigDecimal storedAmount = existingPayment.getAmount();
        if (storedAmount == null) {
            throw new IllegalStateException("?쒕쾭????λ맂 寃곗젣 湲덉븸???놁뒿?덈떎.");
        }
        if (storedAmount.compareTo(BigDecimal.valueOf(amount)) != 0) {
            throw new IllegalStateException("?붿껌 湲덉븸???쒕쾭????λ맂 湲덉븸怨??쇱튂?섏? ?딆뒿?덈떎.");
        }

        // ?좎뒪 ?뱀씤 API ?몄텧 (?몃? ?대씪?댁뼵?몄뿉 ?꾩엫)
        TossConfirmResult tossResult =
                tossPaymentsClient.confirmPayment(paymentKey, orderId, amount);

        Payments confirmedPayment = Payments.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .status("DONE")
                .payMethod(tossResult.getPayMethod())
                .pgRawResponse(tossResult.getRawJson())
                .cardCompany(tossResult.getCardCompany())
                .cardApprovalNo(tossResult.getApproveNo())
                .approvedAt(tossResult.getApprovedAt())
                .build();

        paymentsMapper.updatePaymentStatus(confirmedPayment);

        // ?ъ씤???ㅼ젣 李④컧
        String userId = existingPayment.getUserId();
        BigDecimal usedPoint = nvl(existingPayment.getUsedPoint());
        if (userId != null && !userId.isEmpty()
                && usedPoint.compareTo(BigDecimal.ZERO) > 0) {
            pointService.usePoint(userId, usedPoint, orderId);
        }

        // 援щ룆沅??쒖꽦??
        subscriptionDomainService.grantSubscriptionToUser(orderId);

        return paymentsMapper.findPaymentByOrderId(orderId).orElseThrow(() ->
                new IllegalStateException("?뱀씤?섏뿀?쇰굹 DB?먯꽌 理쒖쥌 議고쉶 ?ㅽ뙣"));
    }

    @Override
    @Transactional(readOnly = true)
    public UpgradeQuoteResponse getUpgradeQuote(String userId, String targetPlanCode) {
        return subscriptionDomainService.getUpgradeQuote(userId, targetPlanCode);
    }

    @Override
    @Transactional
    public Payments cancelPayment(String paymentKey, String cancelReason) {

        Payments paymentToCancel = paymentsMapper.findPaymentByPaymentKey(paymentKey)
                .orElseThrow(() ->
                        new IllegalArgumentException("痍⑥냼??寃곗젣 ?뺣낫瑜?李얠쓣 ???놁뒿?덈떎."));

        if ("CANCELED".equals(paymentToCancel.getStatus())) {
            throw new IllegalStateException("?대? 痍⑥냼??寃곗젣?낅땲??");
        }

        if (!"DONE".equals(paymentToCancel.getStatus())) {
            throw new IllegalStateException(
                    "寃곗젣 ?꾨즺 ?곹깭?먯꽌留??섎텋?????덉뒿?덈떎. (?꾩옱 ?곹깭: " + paymentToCancel.getStatus() + ")");
        }

        LocalDateTime requestedAt = paymentToCancel.getRequestedAt();
        if (requestedAt != null && requestedAt.isBefore(LocalDateTime.now().minusDays(7))) {
            throw new IllegalArgumentException("寃곗젣 ??7?쇱씠 吏??嫄댁? ?섎텋?????놁뒿?덈떎.");
        }

        // ?좎뒪 ?섎텋 API ?몄텧
        String newStatus = tossPaymentsClient.cancelPayment(paymentKey, cancelReason);

        paymentsMapper.updatePaymentStatusToCanceled(paymentToCancel.getOrderId(), newStatus);
        subscriptionDomainService.cancelSubscriptionByOrderId(paymentToCancel.getOrderId());

        String userId = paymentToCancel.getUserId();
        BigDecimal usedPoint = nvl(paymentToCancel.getUsedPoint());
        if (userId != null && !userId.isEmpty()
                && usedPoint.compareTo(BigDecimal.ZERO) > 0) {
            pointService.refundPoint(userId, usedPoint, paymentToCancel.getOrderId(), cancelReason);
        }

        return paymentsMapper.findPaymentByOrderId(paymentToCancel.getOrderId())
                .orElse(paymentToCancel);
    }

    // ================== ??븷蹂?private 硫붿냼??==================

    private String validateUserAndInitOrderId(Payments payments) {
        String userId = payments.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("寃곗젣瑜?吏꾪뻾?섎젮硫?userId媛 ?꾩슂?⑸땲??");
        }

        // orderId ?놁쑝硫??쒕쾭?먯꽌 ?앹꽦
        if (payments.getOrderId() == null || payments.getOrderId().isBlank()) {
            String newOrderId = "ORD-" + System.currentTimeMillis()
                    + "-" + UUID.randomUUID().toString().substring(0, 8);
            payments.setOrderId(newOrderId);
        }
        return userId;
    }

    /**
     * 湲덉븸/?ъ씤???뺢퇋??+ ?좏슚??寃??+ ?ъ씤???붿븸 寃利?
     */
    
    private boolean normalizeAmountsAndValidate(Payments payments, String userId) {

        BigDecimal clientAmount   = nvl(payments.getAmount());          // ????? ??? ?? ?? ??
        BigDecimal originalAmount = nvl(payments.getOriginalAmount());  // ?? ?? ??
        BigDecimal usedPoint      = nvl(payments.getUsedPoint());       // ??? ???

        BigDecimal serverPrice =
                subscriptionDomainService.getMonthlyPrice(payments.getPlanCode());

        BigDecimal upgradeExtra = getUpgradeExtraAmountIfApplicable(userId, payments.getPlanCode());
        if (upgradeExtra != null && upgradeExtra.compareTo(BigDecimal.ZERO) > 0) {
            serverPrice = upgradeExtra;
        }

        // 비활성 플랜이거나 가격이 0 이하인데 FREE/업그레이드 예외도 아니라면 차단
        if (serverPrice.compareTo(BigDecimal.ZERO) <= 0
                && !"FREE".equalsIgnoreCase(payments.getPlanCode())) {
            throw new IllegalArgumentException("유효하지 않은 결제 플랜 코드입니다: " + payments.getPlanCode());
        }

        originalAmount = applyServerPriceIfNecessary(
                payments, originalAmount, clientAmount, usedPoint, serverPrice
        );

        // ?ъ슜 ?ъ씤?멸? ?먭툑蹂대떎 ?щ㈃ ?먭툑源뚯?留??ъ슜?섎룄濡??쒕쾭?먯꽌 蹂댁젙
        if (usedPoint.compareTo(originalAmount) > 0) {
            usedPoint = originalAmount;
        }

        validateAmountRanges(originalAmount, usedPoint);

        BigDecimal expectedAmount = originalAmount.subtract(usedPoint);
        if (expectedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("?? ?? ??? 0 ??? ? ????.");
        }

        boolean pointOnly = expectedAmount.compareTo(BigDecimal.ZERO) == 0;

        if (!pointOnly) {
            if (clientAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("?? ??? ???? ????.");
            }
            if (clientAmount.compareTo(expectedAmount) != 0) {
                throw new IllegalArgumentException("??? ?? ??? ??? ?? ??? ???? ????.");
            }
        }

        payments.setAmount(expectedAmount);
        payments.setOriginalAmount(originalAmount);
        payments.setUsedPoint(usedPoint);

        if (usedPoint.compareTo(BigDecimal.ZERO) > 0) {
            pointService.validatePointBalance(userId, usedPoint);
        }

        payments.setStatus(pointOnly ? "DONE" : "READY");
        payments.setRequestedAt(LocalDateTime.now());

        return pointOnly;
    }


    private BigDecimal getUpgradeExtraAmountIfApplicable(String userId, String planCode) {
        if (planCode == null || userId == null) {
            return null;
        }
        if (!"PRO".equalsIgnoreCase(planCode)) {
            return null;
        }

        try {
            UpgradeQuoteResponse quote = subscriptionDomainService.getUpgradeQuote(userId, planCode);
            if (quote != null
                    && quote.isUpgrade()
                    && quote.getExtraAmount() != null
                    && quote.getExtraAmount().compareTo(BigDecimal.ZERO) > 0) {
                return quote.getExtraAmount();
            }
        } catch (Exception e) {
            System.err.println("upgrade quote ?? ??: " + e.getMessage());
        }

        return null;
    }

    private BigDecimal applyServerPriceIfNecessary(Payments payments,
                                                   BigDecimal originalAmount,
                                                   BigDecimal clientAmount,
                                                   BigDecimal usedPoint,
                                                   BigDecimal serverPrice) {

        if (serverPrice.compareTo(BigDecimal.ZERO) > 0) {
            // ?쒕쾭 ?뺢?媛 ?덈뒗 ?뚮옖
            payments.setOriginalAmount(serverPrice);
            return serverPrice;
        }

        // ?쒕쾭 ?뺢?媛 ?녿뒗 ?뱀닔 耳?댁뒪 + ?ъ씤??誘몄궗?????대씪 amount 瑜??먭?濡??ъ슜
        if (serverPrice.compareTo(BigDecimal.ZERO) <= 0
                && originalAmount.compareTo(BigDecimal.ZERO) <= 0
                && usedPoint.compareTo(BigDecimal.ZERO) == 0) {

            if (clientAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("寃곗젣 湲덉븸???щ컮瑜댁? ?딆뒿?덈떎.");
            }
            payments.setOriginalAmount(clientAmount);
            return clientAmount;
        }

        return originalAmount;
    }

    private void validateAmountRanges(BigDecimal originalAmount, BigDecimal usedPoint) {
        if (originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("?먮옒 寃곗젣 湲덉븸???щ컮瑜댁? ?딆뒿?덈떎.");
        }
        if (usedPoint.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("?ъ슜 ?ъ씤?몃뒗 0蹂대떎 ?묒쓣 ???놁뒿?덈떎.");
        }
        if (usedPoint.compareTo(originalAmount) > 0) {
            throw new IllegalArgumentException("?ъ슜 ?ъ씤?멸? 寃곗젣 湲덉븸蹂대떎 ?????놁뒿?덈떎.");
        }
    }

    /**
     * 寃곗젣 row upsert (INSERT or UPDATE), DONE ?곹깭硫?湲곗〈 row ?ъ궗??
     */
    private Payments upsertPayment(Payments payments) {
        Optional<Payments> existingOpt =
                paymentsMapper.findPaymentByOrderId(payments.getOrderId());

        if (existingOpt.isEmpty()) {
            paymentsMapper.insertPayment(payments);
            return payments;
        }

        Payments existing = existingOpt.get();

        // ?대? DONE?대㈃ ?ㅼ떆 泥섎━ X (?ъ씤??援щ룆 以묐났 諛⑹?)
        if ("DONE".equalsIgnoreCase(existing.getStatus())) {
            return existing;
        }

        existing.setUserId(payments.getUserId());
        existing.setPlanCode(payments.getPlanCode());
        existing.setOrderName(payments.getOrderName());
        existing.setCustomerName(payments.getCustomerName());
        existing.setOriginalAmount(payments.getOriginalAmount());
        existing.setUsedPoint(payments.getUsedPoint());
        existing.setAmount(payments.getAmount());
        existing.setStatus(payments.getStatus());
        existing.setRequestedAt(payments.getRequestedAt());

        // ?대쫫? READY吏留?status ?꾨뱶???곕씪 DONE ?????媛??
        paymentsMapper.updatePaymentForReady(existing);
        return existing;
    }

    /**
     * ?ъ씤???꾩븸 寃곗젣 ?뚮줈??泥섎━
     */
    private void handlePointOnlyFlow(String userId, Payments persisted) {

        BigDecimal usedPoint = nvl(persisted.getUsedPoint());

        // ?ъ씤??李④컧
        if (usedPoint.compareTo(BigDecimal.ZERO) > 0) {
            pointService.usePoint(userId, usedPoint, persisted.getOrderId());
        }

        // 寃곗젣?섎떒/?뱀씤 ?뺣낫 媛꾨떒 湲곕줉
        Payments doneUpdate = Payments.builder()
                .orderId(persisted.getOrderId())
                .paymentKey(null)
                .status("DONE")
                .payMethod("POINT_ONLY")
                .approvedAt(LocalDateTime.now())
                .pgRawResponse("POINT_ONLY")
                .build();

        paymentsMapper.updatePaymentStatus(doneUpdate);

        // 援щ룆沅?遺??
        subscriptionDomainService.grantSubscriptionToUser(persisted.getOrderId());

        persisted.setStatus("DONE");
    }

    // ================== 怨듯넻 ?좏떥 ==================

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

        LocalDateTime latestRequestedAt = latest.getRequestedAt();
        if (latestRequestedAt == null) {
            return false;
        }

        return latestRequestedAt.isAfter(LocalDateTime.now().minusDays(30));
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}


