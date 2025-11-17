package kr.or.kosa.backend.user.controller;

import jakarta.validation.Valid;
import kr.or.kosa.backend.user.dto.UserLoginRequestDto;
import kr.or.kosa.backend.user.dto.UserRegisterRequestDto;
import kr.or.kosa.backend.user.dto.UserResponseDto;
import kr.or.kosa.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> register(
            @Valid @ModelAttribute UserRegisterRequestDto dto,
            BindingResult bindingResult,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(bindingResult.getFieldErrors());
        }

        // 회원가입 (이미 enabled = true 상태)
        int userId = userService.register(dto, image);

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "message", "회원가입이 성공적으로 완료되었습니다.",
                        "userId", userId
                )
        );
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserLoginRequestDto dto) {
        UserResponseDto user = userService.login(dto);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable Integer id) {
        return ResponseEntity.ok(userService.getById(id));
    }
}