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

    int updateUserInfo(@Param("id") Long id,
                       @Param("name") String name,
                       @Param("nickname") String nickname);

    int scheduleDelete(@Param("id") Long userId,
                       @Param("deletedAt") LocalDateTime deletedAt);

    int restoreUser(@Param("id") Long userId);

    List<Users> findUsersToDelete(@Param("now") LocalDateTime now);

    int softDeleteUser(@Param("id") Long userId);

    int anonymizeUser(@Param("id") Long userId,
                      @Param("email") String email,
                      @Param("name") String name);

    List<Users> selectUsersByIds(@Param("userIds") List<Long> userIds);

    // 1) provider + providerId 로 Users 조회
    Users findBySocialProvider(@Param("provider") String provider,
                               @Param("providerId") String providerId);

    // 2) SOCIALLOGIN 테이블에 연동 정보 저장
    int insertSocialAccount(
            @Param("userId") Long userId,
            @Param("provider") String provider,
            @Param("providerId") String providerId,
            @Param("email") String email
    );
}