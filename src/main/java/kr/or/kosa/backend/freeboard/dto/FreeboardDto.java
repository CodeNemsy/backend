package kr.or.kosa.backend.freeboard.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FreeboardDto {
    private Long freeboardId;
    private Long userId;
    private String freeboardTitle;
    private List<FreeboardBlockResponse> blocks;
    private String freeboardRepresentImage;
    private Long freeboardClick;
    private LocalDateTime freeboardCreatedAt;
    private List<String> tags;

    // blocks를 JSON 문자열로 변환
    public String toJsonContent(ObjectMapper objectMapper) throws Exception {
        // 이모지 이미지를 유니코드로 변환
        List<FreeboardBlockResponse> processedBlocks = blocks.stream()
                .map(this::convertEmojiImageToUnicode)
                .toList();
        return objectMapper.writeValueAsString(processedBlocks);
    }

    // 이모지 이미지를 유니코드로 변환
    private FreeboardBlockResponse convertEmojiImageToUnicode(FreeboardBlockResponse block) {
        if (!"tiptap".equals(block.getType()) || block.getContent() == null) {
            return block;
        }

        String content = block.getContent().toString();

        // <img> 태그에서 이모지 유니코드 추출 패턴
        // data-emoji="👋" 또는 alt="👋" 형태
        Pattern pattern = Pattern.compile(
                "<img[^>]*(?:data-emoji|alt)=[\"']([^\"']+)[\"'][^>]*>",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(content);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String emoji = matcher.group(1);
            // 이모지인 경우에만 변환 (일반 이미지는 그대로 유지)
            if (isEmoji(emoji)) {
                matcher.appendReplacement(result, emoji);
            }
        }
        matcher.appendTail(result);

        return FreeboardBlockResponse.builder()
                .id(block.getId())
                .type(block.getType())
                .content(result.toString())
                .language(block.getLanguage())
                .order(block.getOrder())
                .build();
    }

    // 이모지인지 확인
    private boolean isEmoji(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        // 유니코드 이모지 범위 체크
        return text.codePoints().anyMatch(codePoint ->
                (codePoint >= 0x1F300 && codePoint <= 0x1F9FF) || // 이모지 & 기호
                        (codePoint >= 0x2600 && codePoint <= 0x26FF) ||   // 기타 기호
                        (codePoint >= 0x2700 && codePoint <= 0x27BF) ||   // Dingbats
                        (codePoint >= 0xFE00 && codePoint <= 0xFE0F) ||   // Variation Selectors
                        (codePoint >= 0x1F600 && codePoint <= 0x1F64F) || // Emoticons
                        (codePoint >= 0x1F680 && codePoint <= 0x1F6FF)    // Transport & Map
        );
    }

    // 순수 텍스트 추출 (검색/RAG용)
    public String toPlainText(ObjectMapper objectMapper) {
        StringBuilder text = new StringBuilder();

        if (freeboardTitle != null) {
            text.append(freeboardTitle).append("\n\n");
        }

        if (blocks != null) {
            for (FreeboardBlockResponse block : blocks) {
                if ("tiptap".equals(block.getType())) {
                    String tiptapText = extractFromTiptap(block.getContent(), objectMapper);
                    text.append(tiptapText).append("\n");
                } else if ("code".equals(block.getType())) {
                    text.append(block.getContent()).append("\n");
                }
            }
        }

        return text.toString().trim();
    }

    // Tiptap JSON에서 텍스트 추출
    private String extractFromTiptap(Object content, ObjectMapper objectMapper) {
        try {
            JsonNode node = objectMapper.valueToTree(content);
            return extractTextFromNode(node);
        } catch (Exception e) {
            return "";
        }
    }

    // JSON 노드에서 재귀적으로 텍스트 추출
    private String extractTextFromNode(JsonNode node) {
        StringBuilder text = new StringBuilder();

        if (node.has("text")) {
            text.append(node.get("text").asText()).append(" ");
        }

        if (node.has("content") && node.get("content").isArray()) {
            for (JsonNode child : node.get("content")) {
                text.append(extractTextFromNode(child));
            }
        }

        return text.toString();
    }
}