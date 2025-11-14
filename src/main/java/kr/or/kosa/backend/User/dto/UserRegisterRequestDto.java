package kr.or.kosa.backend.user.dto;

import lombok.Data;

@Data
public class UserRegisterRequestDto {
    private String email;
    private String password;
    private String name;
    private String nickname;
    private String image;
}