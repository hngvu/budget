package so.budget.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@EnableJpaAuditing
@SpringBootApplication
public class BudgetApiApplication {

    public static void main(String[] args) {
        loadDotenv();
        SpringApplication.run(BudgetApiApplication.class, args);
    }

    /**
     * Nạp các biến môi trường từ file .env vào System Properties khi khởi động.
     * Cho phép cấu hình database (local/cloud) mà không cần bật docker container.
     */
    private static void loadDotenv() {
        Path[] envCandidates = new Path[]{
                Path.of(".env"),
                Path.of("api/.env"),
                Path.of("../.env")
        };

        for (Path path : envCandidates) {
            if (Files.exists(path)) {
                try {
                    for (String line : Files.readAllLines(path)) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                            continue;
                        }
                        int idx = trimmed.indexOf('=');
                        String key = trimmed.substring(0, idx).trim();
                        String value = trimmed.substring(idx + 1).trim();

                        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }

                        // Chỉ set nếu môi trường hệ thống chưa có biến này
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                    break;
                } catch (IOException ignored) {
                }
            }
        }
    }
}
