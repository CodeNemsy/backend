package kr.or.kosa.backend.codenose.service;

import kr.or.kosa.backend.codenose.dto.dtoReal.CodeResultDTO;
import kr.or.kosa.backend.codenose.dto.dtoReal.UserCodePatternDTO;
import kr.or.kosa.backend.codenose.mapper.AnalysisMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightsService {

    private final AnalysisMapper analysisMapper;

    /**
     * 사용자별 코드 분석 결과 조회
     * @param userId 사용자 ID
     * @return 코드 분석 결과 리스트
     */
    public List<CodeResultDTO> getCodeResult(Long userId) {
        log.info("코드 분석 결과 조회 - userId: {}", userId);
        return analysisMapper.findCodeResultByUserId(userId);
    }

    /**
     * 사용자별 코드 패턴 조회
     * @param userId 사용자 ID
     * @return 사용자 코드 패턴 리스트
     */
    public List<UserCodePatternDTO> getUserCodePatterns(Long userId) {
        log.info("사용자 코드 패턴 조회 - userId: {}", userId);
        return analysisMapper.findAllPatternsByUserId(userId);
    }

    /**
     * 특정 분석 결과 상세 조회
     * @param analysisId 분석 ID
     * @return 코드 분석 결과
     */
    public CodeResultDTO getAnalysisDetail(String analysisId) {
        log.info("분석 결과 상세 조회 - analysisId: {}", analysisId);
        return analysisMapper.findCodeResultById(analysisId);
    }

    /**
     * 사용자의 특정 패턴 조회
     * @param userId 사용자 ID
     * @param patternType 패턴 타입
     * @return 사용자 코드 패턴
     */
    public UserCodePatternDTO getSpecificPattern(Long userId, String patternType) {
        log.info("특정 패턴 조회 - userId: {}, patternType: {}", userId, patternType);
        return analysisMapper.findUserCodePattern(userId, patternType);
    }
}