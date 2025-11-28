package kr.or.kosa.backend.users.mapper;

import kr.or.kosa.backend.users.domain.Users;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserMapper {

    Users findByEmail(@Param("email") String email);
    Users findByNickname(@Param("nickname") String nickname);
    Users findById(@Param("id") Long id);

    int insertUser(Users users);

    int updateUserImage(@Param("id") Long id, @Param("image") String image);

    int updatePassword(@Param("id") Long id, @Param("password") String password);

    int updateUserInfo(@Param("id") Long id, @Param("name") String name, @Param("nickname") String nickname);

    int updateUserEmail(@Param("id") Long id, @Param("email") String email);

    int scheduleDelete(@Param("id") Long userId, @Param("deletedAt") LocalDateTime deletedAt);

    int restoreUser(@Param("id") Long userId);

    List<Users> findUsersToDelete(@Param("now") LocalDateTime now);

    int softDeleteUser(@Param("id") Long userId);

    int anonymizeUser(@Param("id") Long userId,
                      @Param("email") String email,
                      @Param("name") String name);

    List<Users> selectUsersByIds(@Param("userIds") List<Long> userIds);

    int insertGithubUser(Users user);

    // 🔥 GitHub OAuth 토큰 저장
    int updateGithubToken(@Param("id") Long id, @Param("githubToken") String githubToken);

    // 🔥 RefreshToken 저장
    int updateUserTokens(@Param("id") Long id, @Param("refreshToken") String refreshToken);

    // 🔥 RefreshToken 조회
    String findRefreshTokenById(@Param("id") Long id);

    // 🔥 RefreshToken 삭제
    int clearRefreshToken(@Param("id") Long id);
}