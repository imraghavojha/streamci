package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.model.Pipeline;
import com.yourname.streamci.streamci.service.PipelineService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import  org.slf4j.LoggerFactory;


import java.util.List;
import java.util.Optional;

@RestController
public class PipelineController {

    private final PipelineService pipelineService;
    private static final Logger logger = LoggerFactory.getLogger(PipelineController.class);

    public PipelineController(PipelineService pipelineService){
        this.pipelineService = pipelineService;
    }

    @GetMapping("/api/pipelines/{id}")
    public ResponseEntity<Pipeline> getPipeline(@PathVariable int id){
        Optional<Pipeline> result = pipelineService.getPipelineById(id);
        if (result.isPresent()) {
            logger.info("Requested pipeline with ID: {}", id);
            return ResponseEntity.ok(result.get());
        } else {
            logger.warn("Pipeline not found for ID: {}", id);
            return ResponseEntity.notFound().build();
        }

    }

    @GetMapping("/api/pipelines")
    public ResponseEntity<List<Pipeline>> printPipelines(){
        logger.info("Requested all pipelines");
        return ResponseEntity.ok(pipelineService.getAllPipelines());
    }

    @PostMapping("/api/pipelines")
    public ResponseEntity<?> addPipeline(@RequestBody Pipeline pipeline){
        if (pipeline.getName() == null || pipeline.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Pipeline name is required");
        }
        logger.info("Received POST request to create pipeline: {}", pipeline.getName());
        Pipeline savedPipeline = pipelineService.savePipeline(pipeline);
        logger.info("Successfully created pipeline with ID: {}", savedPipeline.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(savedPipeline);
    }

    @PutMapping("/api/pipelines/{id}")
    public ResponseEntity<Pipeline> updatePipeline(@PathVariable Integer id, @RequestBody Pipeline pipeline){
        Optional<Pipeline> updatedPipeline = pipelineService.updatePipeline(id, pipeline);
        if (updatedPipeline.isEmpty()){
            return ResponseEntity.notFound().build();
        } else {
            return ResponseEntity.ok(updatedPipeline.get());
        }
    }

    @DeleteMapping("/api/pipelines/{id}")
    public ResponseEntity<Void> deletePipeline(@PathVariable Integer id){
        boolean isDeleted = pipelineService.deletePipeline(id);
        if (isDeleted){
            logger.info("Successfully deleted pipeline with ID: {}", id);
            return ResponseEntity.noContent().build();
        } else {
            logger.info("Couldn't delete pipeline with ID: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

}
