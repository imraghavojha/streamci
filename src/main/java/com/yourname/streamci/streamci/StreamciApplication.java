// Add this to your StreamciApplication.java main class

package com.yourname.streamci.streamci;

import com.yourname.streamci.streamci.service.PipelineService;
import com.yourname.streamci.streamci.*;
import com.yourname.streamci.streamci.model.Pipeline;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class StreamciApplication implements CommandLineRunner {

    //dependency injection
    private final PipelineService pipelineService;

    public StreamciApplication(PipelineService pipelineService){
        this.pipelineService = pipelineService;
    }

    public static void main(String[] args) {
        SpringApplication.run(StreamciApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // Spring calls this AFTER startup but BEFORE becoming web server
    @Override
    public void run(String... args){
        System.out.println("Started SpringBoot running pipeline demo");
        ArrayList<Pipeline> data = (ArrayList<Pipeline>) pipelineService.getAllPipelines();

        for(Pipeline pipeline : data) {
            System.out.println(pipeline);
        }

        System.out.println("Pipeline demo complete! App now ready for web requests.");
    }
}