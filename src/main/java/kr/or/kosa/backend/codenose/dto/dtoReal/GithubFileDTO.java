package kr.or.kosa.backend.codenose.dto.dtoReal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * GitHub 파일 DTO
 * GitHub API 요청/응답 및 DB 저장용
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GithubFileDTO {
    // === DB 컬럼 매핑 ===
    private String fileId;              // FILE_ID (PK) - UUID
    private Long userId;                // USER_ID
    private String repositoryUrl;       // REPOSITORY_URL
    private String owner;               // OWNER
    private String repo;                // REPO
    private String filePath;            // FILE_PATH
    private String fileName;            // FILE_NAME (원래 name)
    private String fileContent;         // FILE_CONTENT (원래 content)
    private Integer fileSize;           // FILE_SIZE (원래 size)
    private String encoding;            // ENCODING
    private LocalDateTime createdAt;    // CREATED_AT
    private LocalDateTime updatedAt;    // UPDATED_AT

    // === GitHub API Response용 별칭 getter/setter ===
    public String getName() {
        return fileName;
    }

    public void setName(String name) {
        this.fileName = name;
    }

    public String getPath() {
        return filePath;
    }

    public void setPath(String path) {
        this.filePath = path;
    }

    public String getContent() {
        return fileContent;
    }

    public void setContent(String content) {
        this.fileContent = content;
    }

    public int getSize() {
        return fileSize != null ? fileSize : 0;
    }

    public void setSize(int size) {
        this.fileSize = size;
    }

    /**
     * GitHub API Response용 생성자
     */
    public GithubFileDTO(String name, String path, String content, String encoding, int size) {
        this.fileName = name;
        this.filePath = path;
        this.fileContent = content;
        this.encoding = encoding;
        this.fileSize = size;
    }
}