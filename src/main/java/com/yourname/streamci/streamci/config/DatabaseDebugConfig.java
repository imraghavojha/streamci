package com.yourname.streamci.streamci.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@Profile("prod")
public class DatabaseDebugConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseDebugConfig.class);

    @Value("${spring.datasource.url:NOT_SET}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:NOT_SET}")
    private String datasourceUsername;

    @Value("${spring.jpa.properties.hibernate.dialect:NOT_SET}")
    private String hibernateDialect;

    @PostConstruct
    public void logDatabaseConfig() {
        logger.info("=== DATABASE CONFIGURATION DEBUG ===");
        logger.info("Active Profiles: {}", System.getProperty("spring.profiles.active"));
        logger.info("Railway Environment: {}", System.getenv("RAILWAY_ENVIRONMENT"));
        logger.info("Port: {}", System.getenv("PORT"));
        logger.info("Datasource URL: {}", maskPassword(datasourceUrl));
        logger.info("Datasource Username: {}", datasourceUsername);
        logger.info("Hibernate Dialect: {}", hibernateDialect);
        logger.info("DATABASE_URL env: {}", maskPassword(System.getenv("DATABASE_URL")));
        logger.info("DATABASE_USERNAME env: {}", System.getenv("DATABASE_USERNAME"));
        logger.info("=====================================");
    }

    private String maskPassword(String url) {
        if (url == null) return "NULL";
        return url.replaceAll(":[^:@]*@", ":***@");
    }
}