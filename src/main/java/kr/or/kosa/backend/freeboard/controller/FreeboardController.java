package kr.or.kosa.backend.freeboard.controller;

import kr.or.kosa.backend.freeboard.domain.Freeboard;
import kr.or.kosa.backend.freeboard.dto.FreeboardDto;
import kr.or.kosa.backend.freeboard.service.FreeboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/freeboard")
@RequiredArgsConstructor
public class FreeboardController {

    private final FreeboardService freeboardService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody FreeboardDto dto,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        // 로그인 구현 전까지 임시로 userId = 1L 사용
        Long actualUserId = (userId != null) ? userId : 1L;
        Long freeboardId = freeboardService.write(dto, actualUserId);

        Map<String, Object> result = new HashMap<>();
        result.put("freeboardId", freeboardId);

        return ResponseEntity.ok(result);
    }

    // 상세 조회
    @GetMapping("/{freeboardId}")
    public ResponseEntity<Freeboard> get(@PathVariable Long freeboardId) {
        Freeboard freeboard = freeboardService.detail(freeboardId);
        return ResponseEntity.ok(freeboard);
    }

    // 목록
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Map<String, Object> result = freeboardService.listPage(page, size);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{freeboardId}")
    public ResponseEntity<Void> update(
            @PathVariable Long freeboardId,
            @RequestBody FreeboardDto dto,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        // 로그인 구현 전까지 임시로 userId = 1L 사용
        Long actualUserId = (userId != null) ? userId : 1L;
        freeboardService.edit(freeboardId, dto, actualUserId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{freeboardId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long freeboardId,
            @RequestAttribute(value = "userId", required = false) Long userId
    ) {
        // 로그인 구현 전까지 임시로 userId = 1L 사용
        Long actualUserId = (userId != null) ? userId : 1L;
        freeboardService.delete(freeboardId, actualUserId);
        return ResponseEntity.ok().build();
    }
}