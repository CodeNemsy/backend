package kr.or.kosa.backend.commons.exception;

import kr.or.kosa.backend.commons.exception.base.BaseBusinessException;
import kr.or.kosa.backend.commons.exception.base.BaseSystemException;
import kr.or.kosa.backend.commons.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Business Exception 처리
     * 팀원들의 기존 코드와 100% 호환
     */
    @ExceptionHandler(BaseBusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBaseBusinessException(
            BaseBusinessException ex
    ){
        log.error("BaseBusinessException: code={}, message={}",
                ex.getErrorCode().getCode(), ex.getMessage());

        String code = ex.getErrorCode().getCode();
        HttpStatus status = determineHttpStatus(code, ex.getMessage());

        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(code, ex.getMessage()));
    }

    /**
     * System Exception 처리
     * 시스템 오류는 항상 500으로 처리
     */
    @ExceptionHandler(BaseSystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleBaseSystemException(
            BaseSystemException ex
    ){
        log.error("BaseSystemException: code={}, message={}",
                ex.getErrorCode().getCode(), ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ex.getErrorCode().getCode(), ex.getMessage()));
    }

    /**
     * Validation Exception 처리
     * @Valid, @NotNull, @NotBlank 등의 검증 실패 시
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex
    ){
        log.error("ValidationException: {}", ex.getMessage());

        // 모든 Validation 에러를 Map으로 수집
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", "입력값 검증 실패", errors));
    }

    /**
     * IllegalArgumentException 처리
     * Service에서 던지는 잘못된 입력 예외
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
            IllegalArgumentException ex
    ){
        log.error("IllegalArgumentException: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("BAD_REQUEST", ex.getMessage()));
    }

    /**
     * SecurityException 처리
     * 권한 관련 예외
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleSecurityException(
            SecurityException ex
    ){
        log.error("SecurityException: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN", ex.getMessage()));
    }

    /**
     * 그 외 모든 예외 처리
     * 예상치 못한 모든 오류를 잡아서 500 반환
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex){
        log.error("Unexpected Exception: ", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."));
    }

    /**
     * HTTP 상태 코드 결정 로직
     *
     * [지원하는 ErrorCode 형식]
     * 1. "404_USER_001" 형식 (권장) - 앞의 숫자를 HTTP 상태 코드로 사용
     * 2. "USER001" 형식 (현재 팀원 코드) - 메시지 기반으로 자동 판단
     *
     * [판단 규칙]
     * - "중복", "이미 사용", "이미 존재" → 409 Conflict
     * - "찾을 수 없", "등록되지 않은", "존재하지 않" → 404 Not Found
     * - "권한이 없", "접근 권한" → 403 Forbidden
     * - "비밀번호", "토큰", "인증" → 401 Unauthorized
     * - "서버", "내부 오류" → 500 Internal Server Error
     * - 기타 → 400 Bad Request
     */
    private HttpStatus determineHttpStatus(String code, String message) {
        // 1. "404_USER_001" 형식 체크 (새로운 형식)
        if (code.matches("^\\d{3}_.*")) {
            try {
                String statusStr = code.split("_")[0];
                int statusCode = Integer.parseInt(statusStr);
                return HttpStatus.valueOf(statusCode);
            } catch (Exception e) {
                log.warn("Failed to extract status code from: {}, falling back to message-based detection", code);
            }
        }

        // 2. "USER001" 형식 - 메시지 기반 판단 (기존 팀원 코드 호환)
        String lowerCode = code.toLowerCase();
        String lowerMessage = message.toLowerCase();

        // 404 Not Found
        if (lowerCode.contains("not_found") ||
                lowerMessage.contains("찾을 수 없") ||
                lowerMessage.contains("등록되지 않은") ||
                lowerMessage.contains("존재하지 않")) {
            return HttpStatus.NOT_FOUND;
        }

        // 409 Conflict (중복)
        if (lowerCode.contains("duplicate") ||
                lowerCode.contains("conflict") ||
                lowerMessage.contains("중복") ||
                lowerMessage.contains("이미 사용") ||
                lowerMessage.contains("이미 존재")) {
            return HttpStatus.CONFLICT;
        }

        // 403 Forbidden (권한 없음)
        if (lowerCode.contains("unauthorized") ||
                lowerCode.contains("forbidden") ||
                lowerMessage.contains("권한이 없") ||
                lowerMessage.contains("접근 권한")) {
            return HttpStatus.FORBIDDEN;
        }

        // 401 Unauthorized (인증 실패)
        if (lowerCode.contains("invalid_password") ||
                lowerCode.contains("invalid_token") ||
                lowerCode.contains("expired_token") ||
                lowerMessage.contains("비밀번호가 일치") ||
                lowerMessage.contains("토큰이") ||
                lowerMessage.contains("인증")) {
            return HttpStatus.UNAUTHORIZED;
        }

        // 500 Internal Server Error
        if (lowerCode.contains("internal") ||
                lowerCode.contains("server_error") ||
                lowerMessage.contains("서버") ||
                lowerMessage.contains("내부 오류")) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        // 기본값: 400 Bad Request
        log.debug("No specific status match for code: {}, message: {}, using 400 Bad Request", code, message);
        return HttpStatus.BAD_REQUEST;
    }
}