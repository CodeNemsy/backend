package kr.or.kosa.backend.auth.github.controller;

import kr.or.kosa.backend.auth.github.dto.GitHubLoginResult;
import kr.or.kosa.backend.auth.github.service.GitHubAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/github")
@RequiredArgsConstructor
public class GitHubAuthController {

    private final GitHubAuthService gitHubAuthService;

    @PostMapping
    public ResponseEntity<GitHubLoginResult> login(@RequestParam("code") String code) {
        GitHubLoginResult result = gitHubAuthService.loginWithGithub(code);
        return ResponseEntity.ok(result);
    }
}