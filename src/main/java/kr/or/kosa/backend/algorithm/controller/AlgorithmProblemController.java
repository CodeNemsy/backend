package kr.or.kosa.backend.algorithm.controller;

import kr.or.kosa.backend.algorithm.domain.AlgoProblem;
import kr.or.kosa.backend.algorithm.service.AlgorithmProblemService;
import kr.or.kosa.backend.commons.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 알고리즘 문제 컨트롤러 - Phase 1
 * 기본 조회 API만 구현
 */
@RestController
@RequestMapping("/api/algo/problems")
@RequiredArgsConstructor
@Slf4j
public class AlgorithmProblemController {

    private final AlgorithmProblemService algorithmProblemService;

    /**
     * 문제 목록 조회 (페이징)
     * GET /api/algo/problems?page=1&size=10
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProblems(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("알고리즘 문제 목록 조회 - page: {}, size: {}", page, size);

        try {
            // 페이지 번호 검증 (1부터 시작)
            if (page < 1) {
                page = 1;
            }
            if (size < 1 || size > 100) {
                size = 10;
            }

            // offset 계산 (0부터 시작)
            int offset = (page - 1) * size;

            // 데이터 조회
            List<AlgoProblem> problems = algorithmProblemService.getProblems(offset, size);
            int totalCount = algorithmProblemService.getTotalProblemsCount();

            // 페이징 정보 계산
            int totalPages = (int) Math.ceil((double) totalCount / size);
            boolean hasNext = page < totalPages;
            boolean hasPrevious = page > 1;

            // 응답 데이터 구성
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("problems", problems);
            responseData.put("currentPage", page);
            responseData.put("pageSize", size);
            responseData.put("totalCount", totalCount);
            responseData.put("totalPages", totalPages);
            responseData.put("hasNext", hasNext);
            responseData.put("hasPrevious", hasPrevious);

            log.info("문제 목록 조회 성공 - 조회된 문제 수: {}", problems.size());

            return ResponseEntity.ok(ApiResponse.success(responseData));

        } catch (Exception e) {
            log.error("문제 목록 조회 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("9999", "문제 목록 조회 중 오류가 발생했습니다."));
        }
    }

    /**
     * 문제 상세 조회
     * GET /api/algo/problems/{problemId}
     */
    @GetMapping("/{problemId}")
    public ResponseEntity<ApiResponse<AlgoProblem>> getProblemDetail(@PathVariable Long problemId) {

        log.info("알고리즘 문제 상세 조회 - problemId: {}", problemId);

        try {
            // 문제 존재 여부 확인
            if (!algorithmProblemService.existsProblem(problemId)) {
                log.warn("존재하지 않는 문제 조회 시도 - problemId: {}", problemId);
                return ResponseEntity.notFound().build();
            }

            // 문제 상세 정보 조회
            AlgoProblem problem = algorithmProblemService.getProblemDetail(problemId);

            log.info("문제 상세 조회 성공 - problemId: {}, title: {}", problemId, problem.getAlgoProblemTitle());

            return ResponseEntity.ok(ApiResponse.success(problem));

        } catch (Exception e) {
            log.error("문제 상세 조회 실패 - problemId: {}", problemId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("9999", "문제 상세 조회 중 오류가 발생했습니다."));
        }
    }

    /**
     * 문제 존재 여부 확인
     * HEAD /api/algo/problems/{problemId}
     */
    @RequestMapping(value = "/{problemId}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> checkProblemExists(@PathVariable Long problemId) {

        log.info("문제 존재 여부 확인 - problemId: {}", problemId);

        try {
            boolean exists = algorithmProblemService.existsProblem(problemId);

            if (exists) {
                log.info("문제 존재 확인 - problemId: {}", problemId);
                return ResponseEntity.ok().build();
            } else {
                log.info("문제 없음 확인 - problemId: {}", problemId);
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("문제 존재 여부 확인 실패 - problemId: {}", problemId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 서버 상태 확인용 (헬스 체크)
     * GET /api/algo/problems/health
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> healthCheck() {

        try {
            int count = algorithmProblemService.getTotalProblemsCount();
            String message = String.format("알고리즘 서비스 정상 동작 중 (총 문제 수: %d)", count);

            return ResponseEntity.ok(ApiResponse.success(message));

        } catch (Exception e) {
            log.error("헬스 체크 실패", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("9999", "서비스 상태 확인 중 오류가 발생했습니다."));
        }
    }
}