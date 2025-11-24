package kr.or.kosa.backend.user.controller;

import jakarta.validation.Valid;
import kr.or.kosa.backend.user.dto.*;
import kr.or.kosa.backend.user.service.UserService;
import kr.or.kosa.backend.security.jwt.JwtProvider;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final JwtProvider jwtProvider;

    private static final String KEY_SUCCESS = "success";
    private static final String KEY_MESSAGE = "message";

    /**
     * 회원가입
     */
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> register(
            @Valid @ModelAttribute UserRegisterRequestDto dto,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        int userId = userService.register(dto, image);
        return ResponseEntity.ok(Map.of(
                KEY_SUCCESS, true,
                KEY_MESSAGE, "회원가입이 완료되었습니다.",
                "userId", userId
        ));
    }

    /**
     * 로그인
     */
    @PostMapping("/login")
    public ResponseEntity<UserLoginResponseDto> login(@RequestBody UserLoginRequestDto dto) {
        return ResponseEntity.ok(userService.login(dto));
    }

    /**
     * AccessToken 재발급
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(Map.of(
                "accessToken", userService.refresh(token)
        ));
    }

    /**
     * 로그아웃
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader("Authorization") String token) {
        userService.logout(token);
        return ResponseEntity.ok(Map.of(
                KEY_SUCCESS, true,
                KEY_MESSAGE, "로그아웃 완료"
        ));
    }

    /**
     * 임시 비밀번호 발급
     */
    @PostMapping("/password/reset/request")
    public ResponseEntity<Map<String, Boolean>> requestPasswordReset(@RequestBody Map<String, String> body) {
        userService.sendPasswordResetLink(body.get("email"));
        return ResponseEntity.ok(Map.of(KEY_SUCCESS, true));
    }

    /**
     * 비밀번호 변경 (로그인 후)
     */
    @PutMapping("/password/update")
    public ResponseEntity<Map<String, Object>> updatePassword(
            @RequestHeader("Authorization") String token,
            @RequestBody PasswordUpdateRequestDto dto
    ) {
        String rawToken = token.replace("Bearer ", "");
        Integer userId = jwtProvider.getUserId(rawToken);

        userService.updatePassword(userId, dto);

        return ResponseEntity.ok(Map.of(
                KEY_SUCCESS, true,
                KEY_MESSAGE, "비밀번호가 성공적으로 변경되었습니다."
        ));
    }
}