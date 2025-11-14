package kr.or.kosa.backend.user.service;

import kr.or.kosa.backend.user.dto.UserRegisterRequestDto;
import kr.or.kosa.backend.user.dto.UserLoginRequestDto;
import kr.or.kosa.backend.user.dto.UserResponseDto;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    int register(UserRegisterRequestDto dto, MultipartFile image);

    UserResponseDto login(UserLoginRequestDto dto);

    UserResponseDto getById(Integer id);
}
