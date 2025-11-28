package kr.or.kosa.backend.auth.github.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GitHubLoginResult {
    private String accessToken;
    private String email;
    private String name;
}