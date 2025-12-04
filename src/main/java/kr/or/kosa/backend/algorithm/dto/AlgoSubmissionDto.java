package kr.or.kosa.backend.algorithm.dto;

import kr.or.kosa.backend.algorithm.dto.enums.JudgeResult;
import kr.or.kosa.backend.algorithm.dto.enums.AiFeedbackStatus;
import kr.or.kosa.backend.algorithm.dto.enums.AiFeedbackType;
import kr.or.kosa.backend.algorithm.dto.enums.GithubCommitStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * 알고리즘 제출 DTO
 * 데이터베이스 테이블: ALGO_SUBMISSIONS
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlgoSubmissionDto {

    // 기본 식별자
    private Long algosubmissionId;
    private Long algoProblemId;
    private Long userId;

    // 제출 코드 및 언어
    private String sourceCode;
    private String language;

    // Judge0 채점 결과
    private JudgeResult judgeResult;
    private Integer executionTime;
    private Integer memoryUsage;
    private Integer passedTestCount;
    private Integer totalTestCount;

    // AI 피드백 관련
    private String aiFeedback;
    private AiFeedbackStatus aiFeedbackStatus;
    private AiFeedbackType aiFeedbackType;

    // 점수 관련
    private BigDecimal focusScore;
    private BigDecimal aiScore;
    private BigDecimal timeEfficiencyScore;
    private BigDecimal finalScore;
    private String scoreWeights;

    // 시간 추적
    private LocalDateTime startSolving;
    private LocalDateTime endSolving;
    private Integer solvingDurationSeconds;

    // Eye Tracking 관련
    private String focusSessionId;
    private Boolean eyetracked;

    // GitHub 연동 관련
    private Boolean githubCommitRequested;
    private GithubCommitStatus githubCommitStatus;

    // 공유 설정
    private Boolean isShared;

    // 타임스탬프
    private LocalDateTime submittedAt;

    // 계산 메서드들

    /**
     * 테스트 통과율 계산 (0.0 ~ 100.0)
     */
    public Double getTestPassRate() {
        if (passedTestCount == null || totalTestCount == null || totalTestCount == 0) {
            return 0.0;
        }
        return (double) passedTestCount / totalTestCount * 100;
    }

    /**
     * 풀이 시간을 분 단위로 변환
     */
    public Integer getSolvingDurationMinutes() {
        if (solvingDurationSeconds == null) {
            return null;
        }
        return solvingDurationSeconds / 60;
    }
}
