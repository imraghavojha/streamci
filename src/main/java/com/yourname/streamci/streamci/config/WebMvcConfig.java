package com.yourname.streamci.streamci.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${cors.allowed.origins:http://localhost:3000}")
    private String allowedOrigins;

    private final Environment environment;

    public WebMvcConfig(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        boolean isTestProfile = environment.acceptsProfiles("test");

        if (isTestProfile) {
            // For tests: Allow all origins but no credentials to avoid conflicts
            registry.addMapping("/api/**")
                    .allowedOriginPatterns("*")  // Use patterns instead of origins
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(false)  // No credentials in tests
                    .maxAge(3600);
        } else {
            // For production: Specific origins with credentials
            registry.addMapping("/api/**")
                    .allowedOrigins(allowedOrigins.split(","))
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .exposedHeaders("Access-Control-Allow-Origin")
                    .maxAge(3600);
        }
    }
}