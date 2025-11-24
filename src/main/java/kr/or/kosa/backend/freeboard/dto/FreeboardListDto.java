package kr.or.kosa.backend.freeboard.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FreeboardListDto {
    private Long freeboardId;
    private Long userId;
    private String freeboardTitle;
    private String freeboardSummary;  // 미리보기용 요약
    private String freeboardRepresentImage;
    private Long freeboardClick;
    private LocalDateTime freeboardCreatedAt;
}
