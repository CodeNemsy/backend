package kr.or.kosa.backend.algorithm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 집중 세션 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FocusSessionDto {
    private String sessionId;
    private Long userId;
    private Long problemId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status; // ACTIVE, COMPLETED, TERMINATED
    private Integer violationCount;
}
