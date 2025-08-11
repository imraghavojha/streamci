package com.yourname.streamci.streamci;

import com.yourname.streamci.streamci.service.PipelineService;
import com.yourname.streamci.streamci.model.Pipeline;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.ArrayList;

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


    // Spring calls this AFTER startup but BEFORE becoming web server
    @Override
    public void run(String... args) throws Exception{
        System.out.println("Started SpringBoot running pipeline demo");
        ArrayList<Pipeline> data = pipelineService.createFakePipelines(5);

        for(Pipeline pipeline : data) {
            System.out.println(pipeline);
        }

        System.out.println("Pipeline demo complete! App now ready for web requests.");
    }
}


