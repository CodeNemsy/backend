package kr.or.kosa.backend.user.controller;

import kr.or.kosa.backend.user.dto.*;
import kr.or.kosa.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public int register(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("name") String name,
            @RequestParam("nickname") String nickname,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {

        UserRegisterRequestDto dto = new UserRegisterRequestDto();
        dto.setEmail(email);
        dto.setPassword(password);
        dto.setName(name);
        dto.setNickname(nickname);

        return userService.register(dto, image);
    }

    @PostMapping("/login")
    public UserResponseDto login(@RequestBody UserLoginRequestDto dto) {
        return userService.login(dto);
    }

    @GetMapping("/{id}")
    public UserResponseDto getUser(@PathVariable Integer id) {
        return userService.getById(id);
    }
}