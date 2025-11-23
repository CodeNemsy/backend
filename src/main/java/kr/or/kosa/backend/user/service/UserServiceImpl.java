package kr.or.kosa.backend.user.service;

import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.infra.s3.S3Uploader;
import kr.or.kosa.backend.security.jwt.JwtProvider;
import kr.or.kosa.backend.user.domain.User;
import kr.or.kosa.backend.user.dto.*;
import kr.or.kosa.backend.user.exception.UserErrorCode;
import kr.or.kosa.backend.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;
    private final S3Uploader s3Uploader;

    private static final long REFRESH_TOKEN_EXPIRE_DAYS = 14;

    // ⭐ 중복 문자열 상수화 (Magic String 제거)
    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";
    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    @Override
    public int register(UserRegisterRequestDto dto, MultipartFile imageFile) {

        if (!emailVerificationService.isVerified(dto.getEmail())) {
            throw new CustomBusinessException(UserErrorCode.EMAIL_NOT_VERIFIED);
        }

        if (userMapper.findByEmail(dto.getEmail()) != null) {
            throw new CustomBusinessException(UserErrorCode.EMAIL_DUPLICATE);
        }

        if (userMapper.findByNickname(dto.getNickname()) != null) {
            throw new CustomBusinessException(UserErrorCode.NICKNAME_DUPLICATE);
        }

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setName(dto.getName());
        user.setNickname(dto.getNickname());
        user.setImage(null);
        user.setEnabled(true);

        userMapper.insertUser(user);
        int userId = user.getId();

        String imageUrl;

        if (imageFile != null && !imageFile.isEmpty()) {
            String folderPath = "profile-images/" + dto.getNickname() + "/profile";
            try {
                imageUrl = s3Uploader.upload(imageFile, folderPath);
            } catch (IOException e) {
                throw new CustomBusinessException(UserErrorCode.FILE_SAVE_ERROR);
            }
        } else {
            imageUrl = "https://codenemsy.s3.ap-northeast-2.amazonaws.com/profile-images/default.png";
        }

        userMapper.updateUserImage(userId, imageUrl);

        return userId;
    }

    @Override
    public UserLoginResponseDto login(UserLoginRequestDto dto) {

        User user = userMapper.findByEmail(dto.getEmail());
        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new CustomBusinessException(UserErrorCode.INVALID_PASSWORD);
        }

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getEmail());

        // ⭐ 상수 사용
        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + user.getId(),
                refreshToken,
                REFRESH_TOKEN_EXPIRE_DAYS,
                TimeUnit.DAYS
        );

        UserResponseDto userDto = UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .nickname(user.getNickname())
                .image(user.getImage())
                .grade(user.getGrade())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .build();

        return UserLoginResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userDto)
                .build();
    }

    @Override
    public String refresh(String bearerToken) {
        String refreshToken = bearerToken.replace("Bearer ", "");

        if (!jwtProvider.validateToken(refreshToken)) {
            throw new CustomBusinessException(UserErrorCode.INVALID_TOKEN);
        }

        Integer userId = jwtProvider.getUserId(refreshToken);

        // ⭐ 상수 사용
        String savedToken = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);

        if (savedToken == null || !savedToken.equals(refreshToken)) {
            throw new CustomBusinessException(UserErrorCode.INVALID_TOKEN);
        }

        return jwtProvider.createAccessToken(userId, jwtProvider.getEmail(refreshToken));
    }

    @Override
    public void logout(String bearerToken) {
        String token = bearerToken.replace("Bearer ", "");

        if (!jwtProvider.validateToken(token)) return;

        Integer userId = jwtProvider.getUserId(token);

        // ⭐ 상수 사용
        redisTemplate.delete(REFRESH_KEY_PREFIX + userId);

        long expireAt = jwtProvider.getTokenRemainingTime(token);
        if (expireAt > 0) {

            // ⭐ 상수 사용
            redisTemplate.opsForValue().set(
                    BLACKLIST_KEY_PREFIX + token,
                    "logout",
                    expireAt,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    @Override
    public UserResponseDto getById(Integer id) {
        User user = userMapper.findById(id);

        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .nickname(user.getNickname())
                .image(user.getImage())
                .grade(user.getGrade())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .build();
    }

    @Override
    public void sendPasswordResetLink(String email) {

        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        String tempPassword = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 10);

        userMapper.updatePassword(
                user.getId(),
                passwordEncoder.encode(tempPassword)
        );

        emailVerificationService.send(
                email,
                "[서비스명] 임시 비밀번호 안내",
                "임시 비밀번호는 아래와 같습니다.\n\n" +
                        tempPassword + "\n\n" +
                        "로그인 후 반드시 비밀번호를 변경해주세요."
        );
    }

    @Override
    public void updatePassword(Integer userId, PasswordUpdateRequestDto dto) {

        User user = userMapper.findById(userId);
        if (user == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new CustomBusinessException(UserErrorCode.INVALID_PASSWORD);
        }

        String encryptedNewPassword = passwordEncoder.encode(dto.getNewPassword());
        userMapper.updatePassword(userId, encryptedNewPassword);
    }
}