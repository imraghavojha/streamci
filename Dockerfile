# backend dockerfile for spring boot
FROM openjdk:17-jdk-slim

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

# expose port
EXPOSE 8080

# run the jar file with production profile
CMD ["java", "-Dspring.profiles.active=prod", "-jar", "target/streamci.jar"]