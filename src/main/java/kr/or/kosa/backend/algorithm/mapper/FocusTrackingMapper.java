package kr.or.kosa.backend.algorithm.mapper;

import kr.or.kosa.backend.algorithm.dto.FocusSessionDto;
import kr.or.kosa.backend.algorithm.dto.FocusSummaryDto;
import kr.or.kosa.backend.algorithm.dto.ViolationLogDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FocusTrackingMapper {

    // 세션 시작
    void startSession(FocusSessionDto session);

    // 세션 종료 및 업데이트
    void updateSession(FocusSessionDto session);

    // 위반 로그 기록
    void insertViolationLog(ViolationLogDto log);

    // 집중 요약 기록
    void insertFocusSummary(FocusSummaryDto summary);

    // 세션 조회
    FocusSessionDto findSessionById(String sessionId);

    // 특정 세션의 위반 로그 조회
    List<ViolationLogDto> findLogsBySessionId(String sessionId);

    // 사용자의 최근 세션 조회 (중복 실행 방지용)
    FocusSessionDto findActiveSessionByUserId(@Param("userId") Long userId, @Param("problemId") Long problemId);
}
