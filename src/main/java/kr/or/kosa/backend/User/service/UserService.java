package kr.or.kosa.backend.user.service;

import kr.or.kosa.backend.user.dto.UserLoginResponseDto;
import kr.or.kosa.backend.user.dto.UserRegisterRequestDto;
import kr.or.kosa.backend.user.dto.UserLoginRequestDto;
import kr.or.kosa.backend.user.dto.UserResponseDto;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    int register(UserRegisterRequestDto dto, MultipartFile imageFile);

    UserLoginResponseDto login(UserLoginRequestDto dto);

    String refresh(String bearerToken);

    void logout(String bearerToken);

    UserResponseDto getById(Integer id);
}
