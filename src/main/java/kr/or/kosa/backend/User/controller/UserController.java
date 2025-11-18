package kr.or.kosa.backend.user.controller;

import jakarta.validation.Valid;
import kr.or.kosa.backend.user.dto.*;
import kr.or.kosa.backend.user.service.UserService;

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

    // ============================
    // 회원가입
    // ============================
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> register(
            @Valid @ModelAttribute UserRegisterRequestDto dto,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        int userId = userService.register(dto, image);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "회원가입이 완료되었습니다.",
                "userId", userId
        ));
    }

    // ============================
    // 로그인 (JWT 발급)
    // ============================
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserLoginRequestDto dto) {
        UserLoginResponseDto response = userService.login(dto);
        return ResponseEntity.ok(response);
    }

    // ============================
    // 액세스 토큰 재발급
    // ============================
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestHeader("Authorization") String token) {
        String newAccessToken = userService.refresh(token);
        return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
    }

    // ============================
    // 로그아웃 (Refresh Token 제거)
    // ============================
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String token) {
        userService.logout(token);
        return ResponseEntity.ok(Map.of("success", true, "message", "로그아웃 완료"));
    }

    // ============================
    // 사용자 조회
    // ============================
    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable Integer id) {
        return ResponseEntity.ok(userService.getById(id));
    }
}