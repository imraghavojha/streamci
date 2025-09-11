package com.yourname.streamci.streamci;

import com.yourname.streamci.streamci.service.PipelineService;
import com.yourname.streamci.streamci.*;
import com.yourname.streamci.streamci.model.Pipeline;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.Arrays;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@EnableCaching
@SpringBootApplication
public class StreamciApplication implements CommandLineRunner {

    //dependency injection
    private final PipelineService pipelineService;

    @Autowired
    private Environment environment;

    public StreamciApplication(PipelineService pipelineService){
        this.pipelineService = pipelineService;
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(StreamciApplication.class);
        app.run(args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // Spring calls this AFTER startup but BEFORE becoming web server
    @Override
    public void run(String... args) {
        System.out.println("🚀 StreamCI Application Started Successfully!");

        // Enhanced profile debugging
        String[] activeProfiles = environment.getActiveProfiles();
        String[] defaultProfiles = environment.getDefaultProfiles();
        System.out.println("📊 Active Profiles: " + Arrays.toString(activeProfiles));
        System.out.println("📊 Default Profiles: " + Arrays.toString(defaultProfiles));
        System.out.println("📊 System Profile Property: " + System.getProperty("spring.profiles.active"));
        System.out.println("📊 Railway Profile Env: " + System.getenv("SPRING_PROFILES_ACTIVE"));
        System.out.println("🔌 Server Port: " + System.getenv("PORT"));

        try {
            ArrayList<Pipeline> data = (ArrayList<Pipeline>) pipelineService.getAllPipelines();
            System.out.println("📋 Found " + data.size() + " existing pipelines");

            for(Pipeline pipeline : data) {
                System.out.println("  ▸ " + pipeline.getName() + " - " + pipeline.getStatus());
            }
        } catch (Exception e) {
            System.out.println("⚠️  Database connection failed: " + e.getMessage());
            e.printStackTrace(); // Add full stack trace for debugging
        }

        System.out.println("✅ StreamCI ready for webhook requests!");
    }
}