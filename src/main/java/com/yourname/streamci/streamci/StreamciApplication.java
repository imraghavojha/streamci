package com.yourname.streamci.streamci;

import com.yourname.streamci.streamci.service.PipelineService;
import com.yourname.streamci.streamci.*;
import com.yourname.streamci.streamci.model.Pipeline;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@EnableCaching
@SpringBootApplication
public class StreamciApplication implements CommandLineRunner {

    //dependency injection
    private final PipelineService pipelineService;

    public StreamciApplication(PipelineService pipelineService){
        this.pipelineService = pipelineService;
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(StreamciApplication.class);

        // Auto-detect Railway environment and set production profile
        if (System.getenv("RAILWAY_ENVIRONMENT") != null || System.getenv("PORT") != null) {
            System.setProperty("spring.profiles.active", "prod");
            System.out.println("🚂 Railway environment detected - activating production profile");
        }

        app.run(args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // Spring calls this AFTER startup but BEFORE becoming web server
    @Override
    public void run(String... args){
        System.out.println("🚀 StreamCI Application Started Successfully!");
        System.out.println("📊 Active Profile: " + System.getProperty("spring.profiles.active", "default"));
        System.out.println("🔌 Server Port: " + System.getenv("PORT"));

        try {
            ArrayList<Pipeline> data = (ArrayList<Pipeline>) pipelineService.getAllPipelines();
            System.out.println("📋 Found " + data.size() + " existing pipelines");

            for(Pipeline pipeline : data) {
                System.out.println("  ▸ " + pipeline.getName() + " - " + pipeline.getStatus());
            }
        } catch (Exception e) {
            System.out.println("⚠️  Could not load pipelines (database might be initializing): " + e.getMessage());
        }

        System.out.println("✅ StreamCI ready for webhook requests!");
    }
}