package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.model.Pipeline;
import com.yourname.streamci.streamci.service.PipelineService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import  org.slf4j.LoggerFactory;

import java.util.ArrayList;

@RestController
public class PipelineController {

    private PipelineService pipelineService;
    private static final Logger logger = LoggerFactory.getLogger(PipelineController.class);

    public PipelineController(PipelineService pipelineService){
        this.pipelineService = pipelineService;
    }

    @GetMapping("api/pipelines/{id}")
    public ResponseEntity<Pipeline> getPipeline(@PathVariable int id){
        logger.info("Requested pipeline with ID: {}", id);
        ArrayList<Pipeline> allPipelines = pipelineService.createFakePipelines(5);
        for (Pipeline pipeline : allPipelines) {
            if (pipeline.getId() == id) {
                logger.info("Successfully found pipeline: {}", pipeline.getName());
                return ResponseEntity.ok(pipeline);
            }
        }
        logger.warn("Pipeline not found for ID: {}", id);
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/api/pipelines")
    public ResponseEntity<ArrayList<Pipeline>> printPipelines(){
        logger.info("Requested all pipelines");
        logger.info("Returning {} pipelines", pipelineService.createFakePipelines(5).size());
        return ResponseEntity.ok(pipelineService.createFakePipelines(5));
    }
    @PostMapping("/api/pipelines")
    public ResponseEntity<Pipeline> addPipeline(@RequestBody Pipeline pipeline){
        logger.info("Received POST request to create pipeline: {}", pipeline.getName());
        pipeline.setId(6);
        logger.info("Successfully created pipeline with ID: {}", pipeline.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(pipeline);
    }
}
