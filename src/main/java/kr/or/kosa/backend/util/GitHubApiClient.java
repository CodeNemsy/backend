package kr.or.kosa.backend.util;

import kr.or.kosa.backend.auth.github.dto.GitHubTokenResponse;
import kr.or.kosa.backend.auth.github.dto.GitHubUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class GitHubApiClient {

    private final WebClient githubClient = WebClient.builder()
            .baseUrl("https://github.com")
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")  // ✅ 수정
            .build();

    /**
     * GitHub Access Token 요청
     */
    public GitHubTokenResponse requestAccessToken(String clientId, String clientSecret, String code) {

        String body = "client_id=" + clientId +
                "&client_secret=" + clientSecret +
                "&code=" + code;

        return githubClient.post()
                .uri("/login/oauth/access_token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(GitHubTokenResponse.class)
                .block();
    }

    /**
     * GitHub 사용자 정보 요청
     */
    public GitHubUserResponse requestUserInfo(String accessToken) {
        return WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build()
                .get()
                .uri("/user")
                .retrieve()
                .bodyToMono(GitHubUserResponse.class)
                .block();
    }
}