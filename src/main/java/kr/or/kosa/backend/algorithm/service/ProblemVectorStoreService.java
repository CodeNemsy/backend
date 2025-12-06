package kr.or.kosa.backend.algorithm.service;

import kr.or.kosa.backend.algorithm.dto.external.ProblemDocumentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 알고리즘 문제 Vector DB 저장/검색 서비스
 * RAG 기반 Few-shot 학습을 위한 문제 데이터 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemVectorStoreService {

    private final VectorStore vectorStore;

    /**
     * 알고리즘 문제를 Vector DB에 저장
     *
     * @param problemDoc 저장할 문제 문서
     * @return 저장된 문서 ID
     */
    public String storeProblem(ProblemDocumentDto problemDoc) {
        String documentId = generateDocumentId(problemDoc);

        Document document = new Document(
                documentId,
                problemDoc.toEmbeddingContent(),
                problemDoc.toMetadata()
        );

        vectorStore.add(List.of(document));

        log.info("✅ Vector DB 저장 완료: [{}] {} ({})",
                problemDoc.getSource(),
                problemDoc.getTitle(),
                problemDoc.getDifficulty());

        return documentId;
    }

    /**
     * 여러 문제를 일괄 저장
     *
     * @param problems 저장할 문제 목록
     * @return 저장된 문서 수
     */
    public int storeProblems(List<ProblemDocumentDto> problems) {
        List<Document> documents = problems.stream()
                .map(p -> new Document(
                        generateDocumentId(p),
                        p.toEmbeddingContent(),
                        p.toMetadata()
                ))
                .toList();

        vectorStore.add(documents);

        log.info("✅ Vector DB 일괄 저장 완료: {}개 문제", documents.size());
        return documents.size();
    }

    /**
     * 유사 문제 검색 (토픽/난이도 기반)
     *
     * @param query 검색 쿼리 (주제, 키워드 등)
     * @param topK  반환할 최대 결과 수
     * @return 유사 문제 문서 목록
     */
    public List<Document> searchSimilarProblems(String query, int topK) {
        log.info("🔍 Vector DB 검색: query='{}', topK={}", query, topK);

        SearchRequest request = SearchRequest.builder()
                .similarityThreshold(0.7)
                .query(query)
                .topK(topK)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        log.info("✅ 검색 결과: {}개 문제", results.size());
        return results;
    }

    /**
     * 특정 소스의 문제만 검색
     *
     * @param query  검색 쿼리
     * @param source 소스 필터 (BOJ, LEETCODE)
     * @param topK   반환할 최대 결과 수
     * @return 필터링된 유사 문제 목록
     */
    public List<Document> searchBySource(String query, String source, int topK) {
        log.info("🔍 Vector DB 검색 (source={}): query='{}', topK={}", source, query, topK);

        String filterExpression = String.format("source == '%s'", source);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(filterExpression)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        log.info("✅ 검색 결과: {}개 문제", results.size());
        return results;
    }

    /**
     * 난이도 범위로 문제 검색
     *
     * @param query      검색 쿼리
     * @param difficulty 난이도 필터
     * @param topK       반환할 최대 결과 수
     * @return 필터링된 유사 문제 목록
     */
    public List<Document> searchByDifficulty(String query, String difficulty, int topK) {
        log.info("🔍 Vector DB 검색 (difficulty={}): query='{}', topK={}", difficulty, query, topK);

        String filterExpression = String.format("difficulty == '%s'", difficulty);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(filterExpression)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        log.info("✅ 검색 결과: {}개 문제", results.size());
        return results;
    }

    /**
     * Few-shot 학습용 유사 문제 검색
     * 태그와 난이도를 기반으로 가장 관련성 높은 문제 반환
     *
     * @param topic      알고리즘 주제 (예: "그래프 탐색", "동적 프로그래밍")
     * @param difficulty 목표 난이도
     * @param count      반환할 예시 문제 수
     * @return Few-shot 학습에 사용할 문제 목록
     */
    public List<Document> getFewShotExamples(String topic, String difficulty, int count) {
        String query = String.format("%s algorithm problem %s level", topic, difficulty);

        log.info("🎯 Few-shot 예시 검색: topic='{}', difficulty='{}', count={}",
                topic, difficulty, count);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(count)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        log.info("✅ Few-shot 예시: {}개 문제 반환", results.size());
        return results;
    }

    /**
     * 문서 고유 ID 생성 (UUID 형식)
     * source + externalId 조합을 기반으로 결정적 UUID 생성
     * - 동일한 문제는 항상 같은 UUID를 가짐 (멱등성 보장)
     * - Qdrant의 UUID 요구사항 충족
     */
    private String generateDocumentId(ProblemDocumentDto problem) {
        String uniqueKey = String.format("%s_%s", problem.getSource(), problem.getExternalId());
        return UUID.nameUUIDFromBytes(uniqueKey.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
