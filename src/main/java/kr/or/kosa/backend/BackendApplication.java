package kr.or.kosa.backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EnableScheduling
@SpringBootApplication
@MapperScan({
        "kr.or.kosa.backend.**.mapper",
        "kr.or.kosa.backend.**.repository"
})
public class BackendApplication {

    private static final Logger log = LoggerFactory.getLogger(BackendApplication.class);

    @Autowired
    private Environment env;

    public static void main(String[] args) {

        // 1) .env 로드
        Dotenv dotenv = Dotenv.configure()
                .directory("./")
                .ignoreIfMissing()
                .load();

        // 2) 환경변수를 시스템 프로퍼티로 등록
        dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
        );

        // 3) Spring Boot 실행
        SpringApplication.run(BackendApplication.class, args);
    }

    /**
     * 서버 실행 후 DB 설정 확인 로그 출력
     */
    @PostConstruct
    public void printDatabaseConfig() {

        if (log.isInfoEnabled()) {
            log.info("======= 🔍 DB CONFIG DEBUG =======");
            log.info("DB URL      : {}", env.getProperty("spring.datasource.url"));
            log.info("DB USERNAME : {}", env.getProperty("spring.datasource.username"));
            log.info("DB PASSWORD : {}", env.getProperty("spring.datasource.password"));
            log.info("==================================");
        }
    }
}