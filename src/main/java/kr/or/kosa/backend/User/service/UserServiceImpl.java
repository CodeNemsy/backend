package kr.or.kosa.backend.user.service;

import kr.or.kosa.backend.user.domain.User;
import kr.or.kosa.backend.user.dto.*;
import kr.or.kosa.backend.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Value("${file.upload-dir}")
    private String uploadDir;  // 실제 서버 저장 경로 (/home/teamproject/coai/uploads/profile-images)

    @Override
    public int register(UserRegisterRequestDto dto, MultipartFile imageFile) {

        // uploadDir 끝에 "/" 자동 추가
        if (!uploadDir.endsWith("/")) {
            uploadDir = uploadDir + "/";
        }

        // 1) 기본 회원 정보 저장
        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPassword(dto.getPassword());
        user.setName(dto.getName());
        user.setNickname(dto.getNickname());
        user.setImage(null);

        userMapper.insertUser(user);
        int userId = user.getId();

        String imageUrl;

        // 2) 프로필 이미지 업로드 O
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                String safeNickname = dto.getNickname()
                        .replaceAll("[^a-zA-Z0-9가-힣_\\-]", "_");

                // 실제 파일 저장 경로
                String userFolder = uploadDir + safeNickname + "/profile/";

                File folder = new File(userFolder);
                if (!folder.exists()) folder.mkdirs();

                // 고유 파일명
                String fileName = System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();

                // 실제 저장
                Path filePath = Paths.get(userFolder + fileName);
                Files.copy(imageFile.getInputStream(), filePath);

                // 웹에서 접근할 URL
                imageUrl = "/profile-images/" + safeNickname + "/profile/" + fileName;

            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("이미지 업로드 실패", e);
            }

        } else {
            // 3) 프로필 이미지 업로드 X → 기본 이미지 적용
            imageUrl = "/profile-images/default.png";
        }

        // DB에 이미지 저장
        userMapper.updateUserImage(userId, imageUrl);

        return userId;
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