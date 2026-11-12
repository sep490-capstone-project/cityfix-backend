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

    @Value("${FIREBASE_CONFIG_PATH:}")
    private String firebaseConfigPath;

    @PostConstruct
    public void initFirebase() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        InputStream serviceAccount = null;
        try {
            // 1. Lấy luồng đọc file (từ VPS hoặc Local)
            if (StringUtils.hasText(firebaseConfigPath)) {
                log.info("Khởi tạo Firebase bằng file mount ngoài: {}", firebaseConfigPath);
                serviceAccount = new FileInputStream(firebaseConfigPath);
            } else {
                log.info("Khởi tạo Firebase bằng file trong classpath resources");
                serviceAccount = new ClassPathResource("firebase-service-account.json").getInputStream();
            }

            // 2. Sử dụng try-with-resources để đảm bảo InputStream luôn được đóng (Close)
            try (InputStream is = serviceAccount) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(is);

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
            // 3. Ném ra RuntimeException để Spring Boot NGỪNG KHỞI ĐỘNG (Fail-fast)
            throw new IllegalStateException("Critical Error: Không thể khởi tạo Firebase. Backend dừng hoạt động.", e);
        }
    }
}