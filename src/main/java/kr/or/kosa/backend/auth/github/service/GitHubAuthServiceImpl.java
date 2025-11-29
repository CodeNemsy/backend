package kr.or.kosa.backend.auth.github.service;

import kr.or.kosa.backend.auth.github.dto.*;
import kr.or.kosa.backend.security.jwt.JwtProvider;
import kr.or.kosa.backend.users.domain.Users;
import kr.or.kosa.backend.users.mapper.UserMapper;
import kr.or.kosa.backend.users.service.UserService;
import kr.or.kosa.backend.util.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GitHubAuthServiceImpl implements GitHubAuthService {

    private final UserService userService;
    private final UserMapper userMapper;
    private final JwtProvider jwtProvider;
    private final GitHubApiClient gitHubApiClient;
    private final StringRedisTemplate redisTemplate;

    @Value("${github.client-id}")
    private String clientId;

    @Value("${github.client-secret}")
    private String clientSecret;

    private static final long REFRESH_TOKEN_EXPIRE_DAYS = 14;
    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";

    @Override
    public GitHubLoginResult loginWithGithub(String code) {

        // 1. GitHub AccessToken 요청
        GitHubTokenResponse token =
                gitHubApiClient.requestAccessToken(clientId, clientSecret, code);

        // 2. GitHub 사용자 정보 요청
        GitHubUserResponse gitHubUser =
                gitHubApiClient.requestUserInfo(token.getAccessToken());

        // 3. 기존 회원 or 신규 회원 생성
        Users user = userService.githubLogin(gitHubUser);

        // 4. GitHub 토큰 저장 (DB)
        userMapper.updateGithubToken(user.getId(), token.getAccessToken());

        // 5. 내부 JWT Access / Refresh 생성
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getEmail());

        // 6. Redis에 RefreshToken 저장
        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + user.getId(),
                refreshToken,
                REFRESH_TOKEN_EXPIRE_DAYS,
                TimeUnit.DAYS
        );

        // 7. DB에 RefreshToken 저장
        userMapper.updateUserTokens(user.getId(), refreshToken);

        // 8. AccessToken을 프론트로 반환
        return new GitHubLoginResult(
                accessToken,
                user.getEmail(),
                user.getName()
        );
    }
}