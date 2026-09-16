# Stage 1: Build source code bằng Maven Wrapper
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy wrapper và file pom.xml trước để cache dependency
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline -B

# Copy toàn bộ code và build JAR
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime chạy ứng dụng với JRE nhẹ
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy file .jar đã build từ Stage 1
COPY --from=build /app/target/*.jar app.jar

# Render tự động map port qua biến PORT (mặc định mở 8080)
EXPOSE 8080

ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]