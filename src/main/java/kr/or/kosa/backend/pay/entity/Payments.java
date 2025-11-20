package kr.or.kosa.backend.pay.entity;

import lombok.*;

/**
 * payments 테이블과 매핑되는 VO (MyBatis용)
 * JPA 사용 안 하므로 @Entity, @Table, @Id 등 전부 제거
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class Payments {

    // 주문 ID (PK 역할)
    private String orderId;

    // 실제 로그인한 유저의 PK(아이디 등)
    private String userId;

    // 구독 플랜 코드 (FREE / BASIC / PRO 등)
    private String planCode;

    // Toss 결제 키
    private String paymentKey;

    // 화면에 보여줄 주문 이름 (예: "Basic 구독 1개월")
    private String orderName;

    // 고객 이름 (표시용)
    private String customerName;

    // 결제 금액 (원)
    private int amount;

    // 결제 상태: READY, DONE, CANCELED 등
    private String status;

    // 결제 요청/생성 일시 (ISO 문자열 등)
    private String requestedAt;
}
