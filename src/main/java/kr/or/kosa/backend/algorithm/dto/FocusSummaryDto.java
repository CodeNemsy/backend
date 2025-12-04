package kr.or.kosa.backend.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 집중 요약 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FocusSummaryDto {
    private Long summaryId;
    private String sessionId;
    private Long userId;
    private Integer totalEvents;
    private Double focusInPercentage;
    private Integer totalViolations;
    private Integer criticalViolations;
    private Double finalFocusScore;
    private String focusGrade;
    private LocalDateTime processedAt;
}
