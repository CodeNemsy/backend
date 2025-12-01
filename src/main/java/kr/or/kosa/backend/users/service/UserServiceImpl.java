package kr.or.kosa.backend.users.service;

import kr.or.kosa.backend.commons.exception.custom.CustomBusinessException;
import kr.or.kosa.backend.infra.s3.S3Uploader;
import kr.or.kosa.backend.security.jwt.JwtProvider;
import kr.or.kosa.backend.users.domain.Users;
import kr.or.kosa.backend.users.dto.*;
import kr.or.kosa.backend.users.exception.UserErrorCode;
import kr.or.kosa.backend.users.mapper.UserMapper;
import kr.or.kosa.backend.auth.github.dto.GitHubUserResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
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
    private final PasswordResetTokenService passwordResetTokenService;

    private static final long REFRESH_TOKEN_EXPIRE_DAYS = 14;
    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";
    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    // ---------------------------------------------------------
    // 회원가입
    // ---------------------------------------------------------
    @Override
    public Long register(UserRegisterRequestDto dto, MultipartFile imageFile) {

        if (!emailVerificationService.isVerified(dto.getEmail())) {
            throw new CustomBusinessException(UserErrorCode.EMAIL_NOT_VERIFIED);
        }

        if (userMapper.findByEmail(dto.getEmail()) != null) {
            throw new CustomBusinessException(UserErrorCode.EMAIL_DUPLICATE);
        }

        if (userMapper.findByNickname(dto.getNickname()) != null) {
            throw new CustomBusinessException(UserErrorCode.NICKNAME_DUPLICATE);
        }

        Users users = new Users();
        users.setEmail(dto.getEmail());
        users.setPassword(passwordEncoder.encode(dto.getPassword()));
        users.setName(dto.getName());
        users.setNickname(dto.getNickname());
        users.setImage(null);
        users.setEnabled(true);

        int result = userMapper.insertUser(users);
        if (result <= 0) {
            throw new CustomBusinessException(UserErrorCode.USER_CREATE_FAIL);
        }

        Long userId = users.getId();

        // 프로필 이미지 업로드
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

    // ---------------------------------------------------------
    // 로그인 (DB + Redis 혼합 방식 적용)
    // ---------------------------------------------------------
    @Override
    public UserLoginResponseDto login(UserLoginRequestDto dto) {

        Users users = userMapper.findByEmail(dto.getEmail());
        if (users == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(dto.getPassword(), users.getPassword())) {
            throw new CustomBusinessException(UserErrorCode.INVALID_PASSWORD);
        }

        String accessToken = jwtProvider.createAccessToken(users.getId(), users.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(users.getId(), users.getEmail());

        // 1) Redis 저장
        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + users.getId(),
                refreshToken,
                REFRESH_TOKEN_EXPIRE_DAYS,
                TimeUnit.DAYS
        );

        UserResponseDto userDto = UserResponseDto.builder()
                .id(users.getId())
                .email(users.getEmail())
                .name(users.getName())
                .nickname(users.getNickname())
                .image(users.getImage())
                .grade(users.getGrade())
                .role(users.getRole())
                .enabled(users.getEnabled())
                .build();

        return UserLoginResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userDto)
                .build();
    }

    // ---------------------------------------------------------
    // Access Token 재발급  (Redis + DB 검증)
    // ---------------------------------------------------------
    @Override
    public String refresh(String bearerToken) {

        String refreshToken = bearerToken.replace("Bearer ", "");

        if (!jwtProvider.validateToken(refreshToken)) {
            throw new CustomBusinessException(UserErrorCode.INVALID_TOKEN);
        }

        Long userId = jwtProvider.getUserId(refreshToken);

        // 1) Redis 검증
        String redisToken = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);
        if (redisToken == null || !redisToken.equals(refreshToken)) {
            throw new CustomBusinessException(UserErrorCode.INVALID_TOKEN);
        }

        return jwtProvider.createAccessToken(userId, jwtProvider.getEmail(refreshToken));
    }

    // ---------------------------------------------------------
    // 로그아웃 (Redis + DB 삭제)
    // ---------------------------------------------------------
    @Override
    public boolean logout(String bearerToken) {

        try {
            String token = bearerToken.replace("Bearer ", "");

            if (!jwtProvider.validateToken(token)) return false;

            Long userId = jwtProvider.getUserId(token);

            // 1) Redis refresh 삭제
            redisTemplate.delete(REFRESH_KEY_PREFIX + userId);

            // AccessToken 블랙리스트 처리
            long expireAt = jwtProvider.getTokenRemainingTime(token);

            if (expireAt > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_KEY_PREFIX + token,
                        "logout",
                        expireAt,
                        TimeUnit.MILLISECONDS
                );
            }

            return true;

        } catch (Exception e) {
            log.error("Logout failed: {}", e.getMessage());
            return false;
        }
    }

    // ---------------------------------------------------------
    // 비밀번호 재설정 이메일 요청
    // ---------------------------------------------------------
    @Override
    public String sendPasswordResetLink(String email) {

        Users users = userMapper.findByEmail(email);
        if (users == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        String token = passwordResetTokenService.createResetToken(users.getId());
        String resetUrl = "http://localhost:5173/reset-password?token=" + token;

        boolean sent = emailVerificationService.send(
                email,
                "[서비스명] 비밀번호 재설정 안내",
                "아래 링크를 클릭하여 비밀번호를 재설정하세요.\n\n" +
                        resetUrl + "\n\n" +
                        "본 링크는 15분 동안 유효합니다."
        );

        if (!sent) {
            throw new CustomBusinessException(UserErrorCode.EMAIL_SEND_FAIL);
        }

        return "비밀번호 재설정 이메일이 발송되었습니다.";
    }

    // ---------------------------------------------------------
    // 비밀번호 재설정
    // ---------------------------------------------------------
    @Override
    public boolean resetPassword(String token, String newPassword) {

        Long userId = passwordResetTokenService.validateToken(token);
        if (userId == null) return false;

        String encryptedPassword = passwordEncoder.encode(newPassword);

        int result = userMapper.updatePassword(userId, encryptedPassword);
        if (result > 0) {

            boolean deleted = passwordResetTokenService.deleteToken(token);
            if (!deleted) {
                log.warn("Reset token deletion failed: {}", token);
            }

            return true;
        }

        return false;
    }

    // ---------------------------------------------------------
    // 로그인 상태에서 비밀번호 변경
    // ---------------------------------------------------------
    @Override
    public boolean updatePassword(Long userId, PasswordUpdateRequestDto dto) {

        Users users = userMapper.findById(userId);
        if (users == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        if (!passwordEncoder.matches(dto.getCurrentPassword(), users.getPassword())) {
            return false;
        }

        int result = userMapper.updatePassword(userId, passwordEncoder.encode(dto.getNewPassword()));
        return result > 0;
    }

    // ---------------------------------------------------------
    // 사용자 정보 수정
    // ---------------------------------------------------------
    @Override
    public UserResponseDto updateUserInfo(Long userId, UserUpdateRequestDto dto, MultipartFile image) {

        Users users = userMapper.findById(userId);
        if (users == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 닉네임 중복 검사
        if (dto.getNickname() != null && !dto.getNickname().isBlank()) {
            Users existingUsers = userMapper.findByNickname(dto.getNickname());
            if (existingUsers != null && !existingUsers.getId().equals(userId)) {
                throw new CustomBusinessException(UserErrorCode.NICKNAME_DUPLICATE);
            }
        }

        int updated = userMapper.updateUserInfo(userId, dto.getName(), dto.getNickname());
        if (updated <= 0) {
            throw new CustomBusinessException(UserErrorCode.UPDATE_FAIL);
        }

        // 이미지 업데이트
        if (image != null && !image.isEmpty()) {
            try {
                String imageUrl = s3Uploader.upload(image, "profile-images/" + userId + "/profile");
                userMapper.updateUserImage(userId, imageUrl);
            } catch (IOException e) {
                throw new CustomBusinessException(UserErrorCode.FILE_SAVE_ERROR);
            }
        }

        Users updatedUsers = userMapper.findById(userId);

        return UserResponseDto.builder()
                .id(updatedUsers.getId())
                .email(updatedUsers.getEmail())
                .name(updatedUsers.getName())
                .nickname(updatedUsers.getNickname())
                .image(updatedUsers.getImage())
                .grade(updatedUsers.getGrade())
                .role(updatedUsers.getRole())
                .enabled(updatedUsers.getEnabled())
                .build();
    }

    // ---------------------------------------------------------
    // 재설정 토큰 유효성 확인
    // ---------------------------------------------------------
    @Override
    public boolean isResetTokenValid(String token) {
        return passwordResetTokenService.validateToken(token) != null;
    }

    // ---------------------------------------------------------
    // 내 정보 조회
    // ---------------------------------------------------------
    @Override
    public UserResponseDto getUserInfo(Long userId) {

        Users users = userMapper.findById(userId);

        if (users == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        return UserResponseDto.builder()
                .id(users.getId())
                .email(users.getEmail())
                .name(users.getName())
                .nickname(users.getNickname())
                .image(users.getImage())
                .grade(users.getGrade())
                .role(users.getRole())
                .enabled(users.getEnabled())
                .build();
    }

    // ============================================================
    // 90일 뒤 탈퇴 예약
    // ============================================================
    @Override
    public boolean requestDelete(Long userId) {

        Users users = userMapper.findById(userId);
        if (users == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        if (Boolean.TRUE.equals(users.getIsDeleted())) {
            throw new CustomBusinessException(UserErrorCode.ALREADY_SCHEDULED_DELETE);
        }

        LocalDateTime deletedAt = LocalDateTime.now().plusDays(90);
        int result = userMapper.scheduleDelete(userId, deletedAt);

        return result > 0;
    }

    // ============================================================
    // 탈퇴 신청 복구
    // ============================================================
    @Override
    public boolean restoreUser(Long userId) {

        Users users = userMapper.findById(userId);
        if (users == null) {
            throw new CustomBusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        if (!Boolean.TRUE.equals(users.getIsDeleted())) {
            return false;
        }

        if (users.getDeletedAt() != null && users.getDeletedAt().isBefore(LocalDateTime.now())) {
            return false;
        }

        int result = userMapper.restoreUser(userId);
        return result > 0;
    }

    // ---------------------------------------------------------
    // GitHub 로그인 (회원 생성만 담당)
    // JWT 발급은 Controller에서 처리하는 것이 정석
    // ---------------------------------------------------------
    @Override
    public Users githubLogin(GitHubUserResponse gitHubUser) {

        String provider = "github";
        String providerId = String.valueOf(gitHubUser.getId());
        String email = gitHubUser.getEmail();  // null 가능

        // -----------------------------------------------------
        // 1) 이미 SOCIALLOGIN에 연동된 경우 → 바로 로그인
        // -----------------------------------------------------
        Users linkedUser = userMapper.findBySocialProvider(provider, providerId);
        if (linkedUser != null) {
            return linkedUser;
        }

        // -----------------------------------------------------
        // 2) 기존 유저가 존재하면 자동 연동 후 로그인
        // -----------------------------------------------------
        if (email != null) {
            Users existingUser = userMapper.findByEmail(email);
            if (existingUser != null) {

                // 자동 연동
                userMapper.insertSocialAccount(
                        existingUser.getId(),
                        provider,
                        providerId,
                        email
                );

                return existingUser;
            }
        }

        // -----------------------------------------------------
        // 3) 기존 유저도 없으면 → 신규 Users 자동 생성
        // -----------------------------------------------------
        Users newUser = new Users();
        newUser.setEmail(email != null ? email : "github-" + providerId + "@noemail.com");
        newUser.setName(gitHubUser.getName());
        newUser.setNickname(gitHubUser.getLogin());
        newUser.setImage(gitHubUser.getAvatar_url());
        newUser.setPassword(passwordEncoder.encode("SOCIAL_USER"));  // 의미없는 기본 PW
        newUser.setRole("ROLE_USER");
        newUser.setEnabled(true);

        // Users INSERT
        userMapper.insertUser(newUser);

        // SOCIALLOGIN INSERT
        userMapper.insertSocialAccount(
                newUser.getId(),
                provider,
                providerId,
                email
        );

        return newUser;
    }
}