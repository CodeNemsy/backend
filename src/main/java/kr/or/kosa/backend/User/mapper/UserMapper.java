package kr.or.kosa.backend.user.mapper;

import kr.or.kosa.backend.user.domain.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {

    User findByEmail(String email);

    User findById(Integer id);

    int insertUser(User user);

    int insertGithubUser(User user);

    int updateUser(User user);

    int updateGithubToken(User user);

    int deleteUser(Integer id);
}