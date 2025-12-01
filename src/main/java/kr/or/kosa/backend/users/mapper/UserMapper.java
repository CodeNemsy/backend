package kr.or.kosa.backend.users.mapper;

import kr.or.kosa.backend.users.domain.Users;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserMapper {

    Users findById(Long id);

    Users findByEmail(String email);

    Users findByNickname(String nickname);

    int insertUser(Users users);

    int updateUserImage(@Param("id") Long id, @Param("image") String image);

    int updatePassword(@Param("id") Long id, @Param("password") String password);

    int updateUserInfo(
            @Param("id") Long id,
            @Param("name") String name,
            @Param("nickname") String nickname
    );

    int scheduleDelete(@Param("id") Long id, @Param("deletedAt") LocalDateTime deletedAt);

    int restoreUser(Long id);

    Users findBySocialProvider(
            @Param("provider") String provider,
            @Param("providerId") String providerId
    );

    int insertSocialAccount(
            @Param("userId") Long userId,
            @Param("provider") String provider,
            @Param("providerId") String providerId,
            @Param("email") String email
    );

    List<Users> selectUsersByIds(@Param("userIds") List<Long> userIds);

    // 1) 자동 삭제 대상 유저 조회
    List<Users> findUsersToDelete(@Param("now") LocalDateTime now);

    // 2) 소프트 삭제(유저 표시만 삭제)
    int softDeleteUser(@Param("id") Long id);

    // 3) GDPR 목적 또는 완전 삭제를 위한 익명화
    int anonymizeUser(
            @Param("id") Long id,
            @Param("email") String email,
            @Param("name") String name
    );
}
