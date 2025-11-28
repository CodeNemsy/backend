package kr.or.kosa.backend.global.util;

import kr.or.kosa.backend.auth.github.dto.GitHubTokenResponse;
import kr.or.kosa.backend.auth.github.dto.GitHubUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GitHubApiClient {

    private final WebClient webClient;

    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";

    public GitHubTokenResponse requestAccessToken(String clientId, String clientSecret, String code) {

        Map<String, String> body = Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "code", code
        );

        return webClient.post()
                .uri(TOKEN_URL)
                .header(HttpHeaders.ACCEPT, "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(GitHubTokenResponse.class)
                .block();
    }

    public GitHubUserResponse requestUserInfo(String accessToken) {
        return webClient.get()
                .uri(USER_URL)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(GitHubUserResponse.class)
                .block();
    }
}