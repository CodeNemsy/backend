package kr.or.kosa.backend.pay.repository;

import kr.or.kosa.backend.pay.entity.Payments;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PaymentsMapper {

    /**
     * 1. 결제 정보 초기 저장 (결제 요청 직후)
     */
    int insertPayment(Payments payments);

    /**
     * 2. 주문 ID로 결제 정보 조회 (승인 전/후 상태 확인용)
     */
    Optional<Payments> findPaymentByOrderId(@Param("orderId") String orderId);

    /**
     * 3. 결제 승인 후 상태 및 paymentKey 업데이트
     */
    int updatePaymentStatus(Payments payments);

    /**
     * paymentKey로 결제 정보 조회 (환불 시 필요)
     */
    Optional<Payments> findPaymentByPaymentKey(@Param("paymentKey") String paymentKey);

    /**
     * 환불/취소 상태로 업데이트
     */
    int updatePaymentStatusToCanceled(@Param("orderId") String orderId,
                                      @Param("status") String status);

    /**
     * 결제 READY 단계에서, 동일 orderId 가 이미 있을 때
     * 기본 정보(금액/플랜/상태/요청시각)를 갱신하는 UPDATE
     */
    int updatePaymentForReady(Payments payments);

    /**
     * 특정 유저의 최근 결제 내역 N개를 조회 (환불 남용 체크용)
     *  - requested_at DESC 기준
     */
    List<Payments> findRecentPaymentsByUser(@Param("userId") String userId,
                                            @Param("limit") int limit);
}
