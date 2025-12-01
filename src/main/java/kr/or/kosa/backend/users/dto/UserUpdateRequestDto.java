package kr.or.kosa.backend.users.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateRequestDto {
    private String name;        // 이름
    private String nickname;    // 닉네임
}