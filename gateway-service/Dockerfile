# ---- Build Stage ----
FROM maven:3.9.5-eclipse-temurin-17 AS builder
WORKDIR /build
ENV MAVEN_OPTS="-Djava.net.preferIPv4Stack=true -Dhttps.protocols=TLSv1.2"
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn dependency:go-offline -DskipTests -Dmaven.wagon.http.retryHandler.count=5 -B
COPY src ./src
RUN mvn clean package -DskipTests -Dmaven.wagon.http.retryHandler.count=5 -B

# ---- Runtime Stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
