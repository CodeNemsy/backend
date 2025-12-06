package kr.or.kosa.backend.auth.github.dto;

import kr.or.kosa.backend.users.dto.UserLoginResponseDto;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GitHubCallbackResponse {
    private boolean linkMode;
    private GitHubUserResponse gitHubUser;
    private UserLoginResponseDto loginResponse;
}