# backend dockerfile for spring boot
FROM eclipse-temurin:17

# set working directory
WORKDIR /app

# copy maven wrapper
COPY mvnw .
COPY .mvn .mvn

# copy pom.xml first for dependency caching
COPY pom.xml .

# download dependencies
RUN ./mvnw dependency:go-offline -B

# copy source code
COPY src src

# build the app
RUN ./mvnw clean package -DskipTests

# Spring Boot reads server.port=${PORT:8080} from application-prod.properties
# This CMD explicitly passes it as system property for extra safety
CMD sh -c "java -Dspring.profiles.active=prod -Dserver.port=${PORT:-8080} -jar target/streamci.jar"