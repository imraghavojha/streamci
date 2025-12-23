package com.yourname.streamci.streamci.controller;

import com.yourname.streamci.streamci.dto.DtoMapper;
import com.yourname.streamci.streamci.dto.request.CreatePipelineRequest;
import com.yourname.streamci.streamci.dto.request.UpdatePipelineRequest;
import com.yourname.streamci.streamci.dto.response.PipelineResponse;
import com.yourname.streamci.streamci.model.Pipeline;
import com.yourname.streamci.streamci.service.PipelineService;
import jakarta.validation.Valid;
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
    private final DtoMapper dtoMapper;
    private static final Logger logger = LoggerFactory.getLogger(PipelineController.class);

    public PipelineController(PipelineService pipelineService, DtoMapper dtoMapper){
        this.pipelineService = pipelineService;
        this.dtoMapper = dtoMapper;
    }

    @GetMapping("/api/pipelines/{id}")
    public ResponseEntity<PipelineResponse> getPipeline(@PathVariable int id){
        Optional<Pipeline> result = pipelineService.getPipelineById(id);
        if (result.isPresent()) {
            logger.info("Requested pipeline with ID: {}", id);
            PipelineResponse response = dtoMapper.toPipelineResponse(result.get());
            return ResponseEntity.ok(response);
        } else {
            logger.warn("Pipeline not found for ID: {}", id);
            return ResponseEntity.notFound().build();
        }

    }

    @GetMapping("/api/pipelines")
    public ResponseEntity<List<PipelineResponse>> printPipelines(){
        logger.info("Requested all pipelines");
        List<Pipeline> pipelines = pipelineService.getAllPipelines();
        List<PipelineResponse> response = dtoMapper.toPipelineResponseList(pipelines);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/pipelines")
    public ResponseEntity<?> addPipeline(@Valid @RequestBody CreatePipelineRequest request){
        // validation is now handled by @Valid annotation
        logger.info("Received POST request to create pipeline: {}", request.getName());
        Pipeline pipeline = dtoMapper.toPipeline(request);
        Pipeline savedPipeline = pipelineService.savePipeline(pipeline);
        PipelineResponse response = dtoMapper.toPipelineResponse(savedPipeline);
        logger.info("Successfully created pipeline with ID: {}", savedPipeline.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/api/pipelines/{id}")
    public ResponseEntity<PipelineResponse> updatePipeline(@PathVariable Integer id,
                                                          @Valid @RequestBody UpdatePipelineRequest request){
        Optional<Pipeline> existingPipeline = pipelineService.getPipelineById(id);
        if (existingPipeline.isEmpty()){
            return ResponseEntity.notFound().build();
        }

        Pipeline pipeline = existingPipeline.get();
        dtoMapper.updatePipelineFromRequest(pipeline, request);
        Pipeline updatedPipeline = pipelineService.savePipeline(pipeline);
        PipelineResponse response = dtoMapper.toPipelineResponse(updatedPipeline);
        return ResponseEntity.ok(response);
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
