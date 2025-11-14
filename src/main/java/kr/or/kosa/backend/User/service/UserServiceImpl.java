package kr.or.kosa.backend.user.service;

import kr.or.kosa.backend.user.domain.User;
import kr.or.kosa.backend.user.dto.*;
import kr.or.kosa.backend.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public int register(UserRegisterRequestDto dto, MultipartFile imageFile) {

        String imageUrl = null;

        // ★ 파일 업로드 처리 (아직 구현 전)
        if (imageFile != null && !imageFile.isEmpty()) {
            imageUrl = "/uploads/" + imageFile.getOriginalFilename();
        }

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPassword(dto.getPassword());
        user.setName(dto.getName());
        user.setNickname(dto.getNickname());
        user.setImage(imageUrl); // ★ 중요한 부분

        return userMapper.insertUser(user);
    }

    @Override
    public UserResponseDto login(UserLoginRequestDto dto) {
        User user = userMapper.findByEmail(dto.getEmail());

        if (user == null || !user.getPassword().equals(dto.getPassword())) {
            return null;
        }

        return toResponseDto(user);
    }

    @Override
    public UserResponseDto getById(Integer id) {
        User user = userMapper.findById(id);

        if (user == null) return null;

        return toResponseDto(user);
    }

    private UserResponseDto toResponseDto(User user) {
        UserResponseDto dto = new UserResponseDto();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setName(user.getName());
        dto.setNickname(user.getNickname());
        dto.setImage(user.getImage());
        dto.setGrade(user.getGrade());
        dto.setRole(user.getRole());
        dto.setEnabled(user.getEnabled());
        return dto;
    }
}