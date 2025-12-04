package kr.or.kosa.backend.freeboard.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FreeboardDetailResponseDto {
    private Long freeboardId;
    private Long userId;
    private String userNickname;
    private String freeboardTitle;
    private String freeboardContent;
    private Long freeboardClick;
    private String freeboardImage;
    private String freeboardRepresentImage;
    private LocalDateTime freeboardCreatedAt;

    @Setter
    private List<String> tags;
}