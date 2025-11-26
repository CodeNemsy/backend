package kr.or.kosa.backend.pay.entity;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class SubscriptionPlan {
    private String planCode;
    private String name;
    private int monthlyFee;
    private boolean active;
}
