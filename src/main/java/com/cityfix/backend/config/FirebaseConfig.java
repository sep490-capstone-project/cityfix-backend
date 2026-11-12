package com.cityfix.backend.config;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;

import java.io.FileInputStream;
import java.io.InputStream;

@Slf4j
@Configuration
public class FirebaseConfig {

    // Lấy giá trị từ biến môi trường FIREBASE_CONFIG_PATH, nếu không có thì để rỗng
    @Value("${FIREBASE_CONFIG_PATH:}")
    private String firebaseConfigPath;

    @PostConstruct
    public void initFirebase() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                InputStream serviceAccount;

                // 1. Kiểm tra nếu đang chạy trên VPS (có biến môi trường FIREBASE_CONFIG_PATH)
                if (StringUtils.hasText(firebaseConfigPath)) {
                    log.info("Khởi tạo Firebase bằng file mount ngoài: {}", firebaseConfigPath);
                    serviceAccount = new FileInputStream(firebaseConfigPath); // Đọc file từ đường dẫn tuyệt đối
                } 
                // 2. Fallback nếu chạy ở local dev (đọc từ src/main/resources)
                else {
                    log.info("Khởi tạo Firebase bằng file trong classpath resources");
                    serviceAccount = new ClassPathResource("firebase-service-account.json").getInputStream();
                }

                GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccount);

                // Khởi tạo HttpTransport can thiệp bỏ nén gzip tự động
                NetHttpTransport transport = new NetHttpTransport.Builder().build();

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .setHttpTransport(transport)
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK initialized successfully");
            }
        } catch (Exception e) {
            log.error("Failed to initialize Firebase Admin SDK: {}", e.getMessage(), e);
        }
    }
}

