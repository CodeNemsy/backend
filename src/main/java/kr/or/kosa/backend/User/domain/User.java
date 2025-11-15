package kr.or.kosa.backend.user.domain;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private Integer id;

    private String email;
    private String password;
    private String name;
    private String nickname;

    private String image;
    private Integer grade;
    private String role;

    private String githubToken;
    private String refreshToken;

    private Boolean isDeleted;

    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Boolean enabled;
}