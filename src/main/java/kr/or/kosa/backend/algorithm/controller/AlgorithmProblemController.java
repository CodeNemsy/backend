package kr.or.kosa.backend.algorithm.controller;

import kr.or.kosa.backend.algorithm.dto.AlgoProblemDto;
import kr.or.kosa.backend.algorithm.dto.request.ProblemGenerationRequestDto;
import kr.or.kosa.backend.algorithm.dto.response.ProblemGenerationResponseDto;
import kr.or.kosa.backend.algorithm.dto.response.ProblemStatisticsResponseDto;
import kr.or.kosa.backend.algorithm.dto.enums.ProblemDifficulty;
import kr.or.kosa.backend.algorithm.exception.AlgoErrorCode;
import kr.or.kosa.backend.algorithm.service.AIProblemGeneratorService;
import kr.or.kosa.backend.algorithm.service.AlgorithmProblemService;
import kr.or.kosa.backend.algorithm.service.ProblemGenerationOrchestrator;
import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.commons.response.ApiResponse;
import kr.or.kosa.backend.security.jwt.JwtAuthentication;
import kr.or.kosa.backend.security.jwt.JwtUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 알고리즘 문제 컨트롤러
 */
@RestController
@RequestMapping("/algo/problems")
@RequiredArgsConstructor
@Slf4j
public class AlgorithmProblemController {

    private final AlgorithmProblemService algorithmProblemService;
    private final AIProblemGeneratorService aiProblemGeneratorService;
    private final ProblemGenerationOrchestrator problemGenerationOrchestrator;
    private final ObjectMapper objectMapper;

    /**
     * AI 문제 생성 및 DB 저장
     * POST /api/algo/problems/generate
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateProblem(
            @RequestBody ProblemGenerationRequestDto request,
            @AuthenticationPrincipal JwtAuthentication authentication) {

        log.info("AI 문제 생성 요청 - 난이도: {}, 주제: {}", request.getDifficulty(), request.getTopic());

        try {
            if (request.getDifficulty() == null) {
                throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);
            }

            if (request.getTopic() == null) {
                throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);
            }

            ProblemGenerationResponseDto aiResponse = aiProblemGeneratorService.generateProblem(request);

            if (aiResponse.getStatus() != ProblemGenerationResponseDto.GenerationStatus.SUCCESS) {
                log.error("AI 문제 생성 실패 - 상태: {}, 에러: {}",
                        aiResponse.getStatus(), aiResponse.getErrorMessage());
                throw new CustomBusinessException(AlgoErrorCode.PROBLEM_GENERATION_FAIL);
            }

            Long userId = null;
            if (authentication != null) {
                JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
                userId = userDetails.id().longValue();
                log.debug("인증된 사용자 - userId: {}, email: {}", userId, userDetails.email());
            } else {
                log.debug("인증되지 않은 사용자 - 익명으로 문제 생성");
            }

            Long problemId = algorithmProblemService.saveGeneratedProblem(aiResponse, userId);

            log.info("AI 문제 생성 및 저장 완료 - 문제 ID: {}, 제목: {}, 생성자 ID: {}, 소요시간: {}초",
                    problemId,
                    aiResponse.getProblem().getAlgoProblemTitle(),
                    userId != null ? userId : "익명",
                    aiResponse.getGenerationTime());

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("problemId", problemId);
            responseData.put("title", aiResponse.getProblem().getAlgoProblemTitle());
            responseData.put("description", aiResponse.getProblem().getAlgoProblemDescription());
            responseData.put("difficulty", aiResponse.getProblem().getAlgoProblemDifficulty());
            responseData.put("testCaseCount", aiResponse.getTestCases() != null ? aiResponse.getTestCases().size() : 0);
            responseData.put("generationTime", aiResponse.getGenerationTime());

            return ResponseEntity.ok(ApiResponse.success(responseData));

        } catch (CustomBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 문제 생성 중 예외 발생", e);
            throw new CustomBusinessException(AlgoErrorCode.PROBLEM_SAVE_FAIL);
        }
    }

    /**
     * AI 문제 생성 (SSE 스트리밍)
     * GET /api/algo/problems/generate/stream
     */
    @GetMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateProblemStream(
            @RequestParam String difficulty,
            @RequestParam String topic,
            @RequestParam(required = false) String problemType,
            @RequestParam(required = false) String additionalRequirements) {

        log.info("AI 문제 생성 스트리밍 요청 - 난이도: {}, 주제: {}, 타입: {}", difficulty, topic, problemType);

        ProblemGenerationRequestDto request = ProblemGenerationRequestDto.builder()
                .difficulty(ProblemDifficulty.valueOf(difficulty))
                .topic(topic)
                .problemType(problemType != null ? problemType : "ALGORITHM")
                .additionalRequirements(additionalRequirements)
                .build();

        return aiProblemGeneratorService.generateProblemStream(request)
                // Spring WebFlux가 TEXT_EVENT_STREAM_VALUE와 함께 SSE 형식을 자동으로 처리하므로
                // 수동으로 "data: " prefix를 추가하지 않음 (중복 방지)
                .onErrorResume(e -> {
                    log.error("스트리밍 중 에러 발생", e);
                    return Flux.just("{\"type\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}");
                })
                .doOnComplete(() -> log.info("스트리밍 완료"))
                .doOnCancel(() -> log.info("스트리밍 취소됨"));
    }

    /**
     * AI 문제 생성 (검증 포함)
     * POST /api/algo/problems/generate/validated
     */
    @PostMapping("/generate/validated")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateValidatedProblem(
            @RequestBody ProblemGenerationRequestDto request,
            @AuthenticationPrincipal JwtAuthentication authentication) {

        log.info("AI 문제 생성 (검증 포함) 요청 - 난이도: {}, 주제: {}", request.getDifficulty(), request.getTopic());

        try {
            if (request.getDifficulty() == null) {
                throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);
            }

            if (request.getTopic() == null) {
                throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);
            }

            Long userId = null;
            if (authentication != null) {
                JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
                userId = userDetails.id().longValue();
            }

            ProblemGenerationResponseDto response = problemGenerationOrchestrator.generateProblem(
                    request, userId, null);

            log.info("AI 문제 생성 (검증 포함) 완료 - 문제 ID: {}, 제목: {}",
                    response.getProblemId(),
                    response.getProblem().getAlgoProblemTitle());

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("problemId", response.getProblemId());
            responseData.put("title", response.getProblem().getAlgoProblemTitle());
            responseData.put("description", response.getProblem().getAlgoProblemDescription());
            responseData.put("difficulty", response.getProblem().getAlgoProblemDifficulty());
            responseData.put("testCaseCount", response.getTestCases() != null ? response.getTestCases().size() : 0);
            responseData.put("validationResults", response.getValidationResults());
            responseData.put("hasOptimalCode", response.getOptimalCode() != null);
            responseData.put("hasNaiveCode", response.getNaiveCode() != null);

            return ResponseEntity.ok(ApiResponse.success(responseData));

        } catch (CustomBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 문제 생성 (검증 포함) 중 예외 발생", e);
            throw new CustomBusinessException(AlgoErrorCode.PROBLEM_GENERATION_FAIL);
        }
    }

    /**
     * AI 문제 생성 (검증 포함, SSE 스트리밍)
     * GET /api/algo/problems/generate/validated/stream
     */
    @GetMapping(value = "/generate/validated/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateValidatedProblemStream(
            @RequestParam String difficulty,
            @RequestParam String topic,
            @RequestParam(required = false) String problemType,
            @RequestParam(required = false) String additionalRequirements) {

        log.info("AI 문제 생성 (검증 포함) 스트리밍 요청 - 난이도: {}, 주제: {}", difficulty, topic);

        ProblemGenerationRequestDto request = ProblemGenerationRequestDto.builder()
                .difficulty(ProblemDifficulty.valueOf(difficulty))
                .topic(topic)
                .problemType(problemType != null ? problemType : "ALGORITHM")
                .additionalRequirements(additionalRequirements)
                .build();

        return Flux.create(sink -> {
            reactor.core.scheduler.Schedulers.boundedElastic().schedule(() -> {
                try {
                    ProblemGenerationResponseDto response = problemGenerationOrchestrator.generateProblem(
                            request,
                            null,
                            progressEvent -> {
                                try {
                                    Map<String, Object> event = new HashMap<>();
                                    event.put("type", "PROGRESS");
                                    event.put("status", progressEvent.getStatus());
                                    event.put("message", progressEvent.getMessage());
                                    event.put("percentage", progressEvent.getPercentage());
                                    sink.next("data: " + objectMapper.writeValueAsString(event) + "\n\n");
                                } catch (Exception e) {
                                    log.error("SSE 진행률 이벤트 전송 실패", e);
                                }
                            }
                    );

                    Map<String, Object> completeEvent = new HashMap<>();
                    completeEvent.put("type", "COMPLETE");
                    completeEvent.put("problemId", response.getProblemId());
                    completeEvent.put("title", response.getProblem().getAlgoProblemTitle());
                    completeEvent.put("testCaseCount", response.getTestCases() != null ? response.getTestCases().size() : 0);
                    completeEvent.put("validationResults", response.getValidationResults());

                    sink.next("data: " + objectMapper.writeValueAsString(completeEvent) + "\n\n");
                    sink.complete();

                    log.info("검증 포함 스트리밍 완료 - 문제 ID: {}", response.getProblemId());

                } catch (Exception e) {
                    log.error("검증 포함 스트리밍 문제 생성 실패", e);
                    try {
                        Map<String, Object> errorEvent = new HashMap<>();
                        errorEvent.put("type", "ERROR");
                        errorEvent.put("message", e.getMessage());
                        sink.next("data: " + objectMapper.writeValueAsString(errorEvent) + "\n\n");
                    } catch (Exception ex) {
                        sink.next("data: {\"type\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}\n\n");
                    }
                    sink.complete();
                }
            });
        });
    }

    /**
     * 문제 목록 조회
     * GET /api/algo/problems?page=1&size=10&difficulty=BRONZE&source=AI_GENERATED&keyword=검색어&topic=배열&problemType=ALGORITHM
     */
//    @GetMapping
//    public ResponseEntity<ApiResponse<Map<String, Object>>> getProblems(
//            @RequestParam(defaultValue = "1") int page,
//            @RequestParam(defaultValue = "10") int size,
//            @RequestParam(required = false) String difficulty,
//            @RequestParam(required = false) String source,
//            @RequestParam(required = false) String keyword,
//            @RequestParam(required = false) String topic,
//            @RequestParam(required = false) String problemType,
//            @AuthenticationPrincipal JwtAuthentication authentication) {
//
//        log.info("문제 목록 조회 요청 - page: {}, size: {}, difficulty: {}, source: {}, keyword: {}, topic: {}, problemType: {}",
//                page, size, difficulty, source, keyword, topic, problemType);
//
//        try {
//            if (page < 1) {
//                page = 1;
//            }
//            if (size < 1 || size > 100) {
//                size = 10;
//            }
//
//            Long userId = null;
//            if (authentication != null) {
//                JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
//                userId = userDetails.id().longValue();
//            }
//
//            int offset = (page - 1) * size;
//
//            List<AlgoProblemDto> problems = algorithmProblemService.getProblemsWithFilter(
//                    offset, size, difficulty, source, keyword, topic, problemType);
//
//            int totalCount = algorithmProblemService.getTotalProblemsCountWithFilter(
//                    difficulty, source, keyword, topic, problemType);
//
//            int totalPages = (int) Math.ceil((double) totalCount / size);
//            boolean hasNext = page < totalPages;
//            boolean hasPrevious = page > 1;
//
//            Map<String, Object> responseData = new HashMap<>();
//            responseData.put("problems", problems);
//            responseData.put("currentPage", page);
//            responseData.put("pageSize", size);
//            responseData.put("totalCount", totalCount);
//            responseData.put("totalPages", totalPages);
//            responseData.put("hasNext", hasNext);
//            responseData.put("hasPrevious", hasPrevious);
//
//            log.info("문제 목록 조회 성공 - 조회된 문제 수: {}", problems.size());
//
//            return ResponseEntity.ok(ApiResponse.success(responseData));
//
//        } catch (Exception e) {
//            log.error("문제 목록 조회 실패", e);
//            throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);
//        }
//    }

    /**
     * 문제 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProblems(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String problemType,
            @RequestAttribute(value = "userId", required = false) Long userId) { // 👈 변경

        log.info("문제 목록 조회 요청 - userId: {}, page: {}, size: {}", userId, page, size);

        try {
            if (page < 1) page = 1;
            if (size < 1 || size > 100) size = 10;

            int offset = (page - 1) * size;

            List<Map<String, Object>> problems = algorithmProblemService.getProblemsWithUserStatus(
                    userId, offset, size, difficulty, source, keyword, topic, problemType);

            int totalCount = algorithmProblemService.getTotalProblemsCountWithFilter(
                    difficulty, source, keyword, topic, problemType);

            int totalPages = (int) Math.ceil((double) totalCount / size);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("problems", problems);
            responseData.put("currentPage", page);
            responseData.put("pageSize", size);
            responseData.put("totalCount", totalCount);
            responseData.put("totalPages", totalPages);
            responseData.put("hasNext", page < totalPages);
            responseData.put("hasPrevious", page > 1);

            return ResponseEntity.ok(ApiResponse.success(responseData));

        } catch (Exception e) {
            log.error("문제 목록 조회 실패", e);
            throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);
        }
    }

    /**
     * 통계 정보 조회
     * GET /api/algo/problems/statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<ProblemStatisticsResponseDto>> getStatistics(
            @AuthenticationPrincipal JwtAuthentication authentication) {

        log.info("통계 정보 조회");

        try {
            Long userId = null;
            if (authentication != null) {
                JwtUserDetails userDetails = (JwtUserDetails) authentication.getPrincipal();
                userId = userDetails.id().longValue();
            }

            ProblemStatisticsResponseDto statistics = algorithmProblemService.getProblemStatistics(userId);

            log.info("통계 정보 조회 성공 - {}", statistics);

            return ResponseEntity.ok(ApiResponse.success(statistics));

        } catch (Exception e) {
            log.error("통계 정보 조회 실패", e);
            throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);
        }
    }

    /**
     * 문제 상세 조회
     * GET /api/algo/problems/{problemId}
     */
    @GetMapping("/{problemId}")
    public ResponseEntity<ApiResponse<AlgoProblemDto>> getProblemDetail(@PathVariable Long problemId) {

        log.info("알고리즘 문제 상세 조회 - problemId: {}", problemId);

        try {
            if (!algorithmProblemService.existsProblem(problemId)) {
                log.warn("존재하지 않는 문제 조회 시도 - problemId: {}", problemId);
                throw new CustomBusinessException(AlgoErrorCode.PROBLEM_NOT_FOUND);
            }

            AlgoProblemDto problem = algorithmProblemService.getProblemDetail(problemId);

            log.info("문제 상세 조회 성공 - problemId: {}, title: {}", problemId, problem.getAlgoProblemTitle());

            return ResponseEntity.ok(ApiResponse.success(problem));

        } catch (CustomBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("문제 상세 조회 실패 - problemId: {}", problemId, e);
            throw new CustomBusinessException(AlgoErrorCode.PROBLEM_NOT_FOUND);
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
            throw new CustomBusinessException(AlgoErrorCode.PROBLEM_NOT_FOUND);
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
            throw new CustomBusinessException(AlgoErrorCode.INVALID_INPUT);
        }
    }
}