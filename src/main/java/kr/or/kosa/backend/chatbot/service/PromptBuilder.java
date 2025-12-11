package kr.or.kosa.backend.chatbot.service;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PromptBuilder {
    // ✅ ChatClient 제거! 프롬프트만 생성하는 순수 서비스

    // 1️⃣ 안내원 시스템 프롬프트 (완벽)
    public String createGuideSystemPrompt(String projectName) {
        String systemPrompt = """
            당신은 {projectName} 프로젝트의 친절한 기술 안내원입니다.
            다음 규칙을 **반드시** 지켜주세요:
            
            1. "안녕하세요, 고객님!"으로 시작
            2. **문제는 대신 풀지 않고 힌트만 제공**
            3. 단계별 안내 (1️⃣ 2️⃣ 3️⃣)
            4. "추가 질문 있으신가요?"로 마무리
            """;

        SystemPromptTemplate template = new SystemPromptTemplate(systemPrompt);
        return template.render(Map.of("projectName", projectName));
    }

    // 2️⃣ 완전한 프롬프트 생성
    public String buildCompleteGuidePrompt(String projectName, String userQuery) {
        String systemPrompt = createGuideSystemPrompt(projectName);
        PromptTemplate userTemplate = new PromptTemplate("고객님 질문: {query}");
        String userPrompt = userTemplate.render(Map.of("query", userQuery));
        return systemPrompt + "\n\n" + userPrompt;
    }
}
