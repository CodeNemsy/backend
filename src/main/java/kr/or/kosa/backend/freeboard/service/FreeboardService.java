package kr.or.kosa.backend.freeboard.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kr.or.kosa.backend.freeboard.domain.Freeboard;
import kr.or.kosa.backend.freeboard.mapper.FreeboardMapper;

@Service
@RequiredArgsConstructor
public class FreeboardService {

    private final FreeboardMapper mapper;

    // 페이지 단위 목록
    public Map<String, Object> listPage(int page, int size) {
        int offset = (page - 1) * size;

        List<Freeboard> boards = mapper.selectPage(offset, size);
        int totalCount = mapper.countAll();

        Map<String, Object> result = new HashMap<>();
        result.put("boards", boards);
        result.put("totalCount", totalCount);
        result.put("page", page);
        result.put("size", size);

        return result;
    }

    // 상세 보기 (조회수 증가 포함)
    public Freeboard detail(Long id) {
        mapper.increaseClick(id);
        return mapper.selectById(id);
    }

    // 작성
    public void write(Freeboard board) {
        String represent = board.getFreeboardRepresentImage();

        // 사용자가 대표 이미지 선택 안하면 첫 번째 이미지를 대표 이미지로
        if (represent == null || represent.isBlank()) {
            represent = extractFirstImage(board.getFreeboardContent());
            board.setFreeboardRepresentImage(represent);
        }

        mapper.insert(board);
    }

    private String extractFirstImage(String content) {
        if (content == null) return null;

        Pattern pattern = Pattern.compile("<img[^>]*src=[\"']?([^>\"']+)[\"']?[^>]*>");
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // 수정
    public void edit(Freeboard board) {
        String represent = board.getFreeboardRepresentImage();

        if (represent == null || represent.isBlank()) {
            represent = extractFirstImage(board.getFreeboardContent());
            board.setFreeboardRepresentImage(represent);
        }

        mapper.update(board);
    }

    // 삭제
    public void delete(Long id) {
        mapper.delete(id);
    }

    // 전체 수
    public int totalCount() {
        return mapper.countAll();
    }
}
