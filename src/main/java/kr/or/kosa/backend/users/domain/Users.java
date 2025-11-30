package kr.or.kosa.backend.users.domain;

import kr.or.kosa.backend.users.dto.UserResponseDto;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Users {

    private Long id;
    private String email;
    private String password;
    private String name;
    private String nickname;
    private String image;
    private Integer grade;
    private String role;
    private String refreshToken;
    private Boolean isDeleted;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean enabled;

    public UserResponseDto toDto() {
        return UserResponseDto.builder()
                .id(this.id)
                .email(this.email)
                .name(this.name)
                .nickname(this.nickname)
                .image(this.image)
                .grade(this.grade)
                .role(this.role)
                .enabled(this.enabled)
                .build();
    }
}