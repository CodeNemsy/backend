package kr.or.kosa.backend.tag.service;

import kr.or.kosa.backend.tag.domain.Tag;
import kr.or.kosa.backend.tag.domain.CodeboardTag;
import kr.or.kosa.backend.tag.domain.FreeboardTag;
import kr.or.kosa.backend.tag.mapper.TagMapper;
import kr.or.kosa.backend.tag.mapper.CodeboardTagMapper;
import kr.or.kosa.backend.tag.mapper.FreeboardTagMapper;
import kr.or.kosa.backend.tag.dto.TagAutocompleteDto;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TagService {
    private final TagMapper tagMapper;
    private final CodeboardTagMapper codeboardTagMapper;
    private final FreeboardTagMapper freeboardTagMapper;

    // 태그 조회 또는 생성
    @Transactional
    public Tag getOrCreateTag(String tagInput) {
        String normalizedName = tagInput.toLowerCase().trim();

        // 기존 태그 조회
        Optional<Tag> existingTag = tagMapper.findByTagName(normalizedName);
        if (existingTag.isPresent()) {
            return existingTag.get();
        }

        // 새 태그 생성
        try {
            Tag newTag = Tag.builder()
                    .tagName(normalizedName)
                    .build();
            tagMapper.insertTag(newTag);
            return newTag;
        } catch (DataIntegrityViolationException e) {
            // 동시성 문제로 UNIQUE 제약 위반 시 재조회
            return tagMapper.findByTagName(normalizedName)
                    .orElseThrow(() -> new RuntimeException("태그 생성 실패"));
        }
    }

    // 코드게시판 게시글에 태그 저장
    @Transactional
    public void attachTagsToCodeboard(Long codeboardId, List<String> tagInputs) {
        if (tagInputs == null || tagInputs.isEmpty()) {
            return;
        }

        for (String tagInput : tagInputs) {
            String trimmedInput = tagInput.trim();
            if (trimmedInput.isEmpty()) {
                continue;
            }

            // 태그 조회 또는 생성
            Tag tag = getOrCreateTag(trimmedInput);

            // 연결 테이블에 저장
            CodeboardTag codeboardTag = CodeboardTag.builder()
                    .codeboardId(codeboardId)
                    .tagId(tag.getTagId())
                    .tagDisplayName(trimmedInput)
                    .build();

            codeboardTagMapper.insert(codeboardTag);
        }
    }

    // 자유게시판 게시글에 태그 저장
    @Transactional
    public void attachTagsToFreeboard(Long freeboardId, List<String> tagInputs) {
        if (tagInputs == null || tagInputs.isEmpty()) {
            return;
        }

        for (String tagInput : tagInputs) {
            String trimmedInput = tagInput.trim();
            if (trimmedInput.isEmpty()) {
                continue;
            }

            // 태그 조회 또는 생성
            Tag tag = getOrCreateTag(trimmedInput);

            // 연결 테이블에 저장
            FreeboardTag freeboardTag = FreeboardTag.builder()
                    .freeboardId(freeboardId)
                    .tagId(tag.getTagId())
                    .tagDisplayName(trimmedInput)
                    .build();

            freeboardTagMapper.insert(freeboardTag);
        }
    }

    // 코드게시판 게시글의 태그 조회
    public List<String> getCodeboardTags(Long codeboardId) {
        return codeboardTagMapper.findByCodeboardId(codeboardId)
                .stream()
                .map(CodeboardTag::getTagDisplayName)
                .toList();
    }

    // 자유게시판 게시글의 태그 조회
    public List<String> getFreeboardTags(Long freeboardId) {
        return freeboardTagMapper.findByFreeboardId(freeboardId)
                .stream()
                .map(FreeboardTag::getTagDisplayName)
                .toList();
    }

    // 코드게시판 게시글 태그 수정
    @Transactional
    public void updateCodeboardTags(Long codeboardId, List<String> tagInputs) {
        // 기존 태그 삭제
        codeboardTagMapper.deleteByCodeboardId(codeboardId);

        // 새 태그 저장
        attachTagsToCodeboard(codeboardId, tagInputs);
    }

    // 자유게시판 게시글 태그 수정
    @Transactional
    public void updateFreeboardTags(Long freeboardId, List<String> tagInputs) {
        // 기존 태그 삭제
        freeboardTagMapper.deleteByFreeboardId(freeboardId);

        // 새 태그 저장
        attachTagsToFreeboard(freeboardId, tagInputs);
    }

    // 자동완성 검색
    public List<TagAutocompleteDto> searchTagsForAutocomplete(String keyword, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String lowerKeyword = keyword.toLowerCase().trim();

        // TAG 테이블에서 검색
        List<Tag> tags = tagMapper.findByTagNameStartingWith(lowerKeyword);

        return tags.stream()
                .limit(limit)
                .map(tag -> {
                    // 가장 많이 사용된 표기 찾기
                    String popularDisplay = tagMapper.findMostUsedDisplayName(tag.getTagId());
                    if (popularDisplay == null) {
                        popularDisplay = tag.getTagName();
                    }

                    // 사용 횟수 계산
                    Long count = tagMapper.countByTagId(tag.getTagId());

                    return TagAutocompleteDto.builder()
                            .tagId(tag.getTagId())
                            .tagDisplayName(popularDisplay)
                            .count(count)
                            .build();
                })
                .sorted((TagAutocompleteDto a, TagAutocompleteDto b) -> Long.compare(b.getCount(), a.getCount())) // 사용 빈도 높은 순
                .toList();
    }

    // 태그로 코드게시판 게시글 검색
    public List<Long> searchCodeboardByTag(String tagDisplay) {
        String normalizedTag = tagDisplay.toLowerCase().trim();

        Optional<Tag> tag = tagMapper.findByTagName(normalizedTag);
        if (tag.isEmpty()) {
            return new ArrayList<>();
        }

        return codeboardTagMapper.findByTagId(tag.get().getTagId())
                .stream()
                .map(CodeboardTag::getCodeboardId)
                .toList();
    }

    // 태그로 자유게시판 게시글 검색
    public List<Long> searchFreeboardByTag(String tagDisplay) {
        String normalizedTag = tagDisplay.toLowerCase().trim();

        Optional<Tag> tag = tagMapper.findByTagName(normalizedTag);
        if (tag.isEmpty()) {
            return new ArrayList<>();
        }

        return freeboardTagMapper.findByTagId(tag.get().getTagId())
                .stream()
                .map(FreeboardTag::getFreeboardId)
                .toList();
    }
}
