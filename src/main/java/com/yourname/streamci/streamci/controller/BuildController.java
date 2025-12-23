package com.yourname.streamci.streamci.controller;


import com.yourname.streamci.streamci.dto.DtoMapper;
import com.yourname.streamci.streamci.dto.request.CreateBuildRequest;
import com.yourname.streamci.streamci.dto.request.UpdateBuildRequest;
import com.yourname.streamci.streamci.dto.response.BuildResponse;
import com.yourname.streamci.streamci.model.Build;
import com.yourname.streamci.streamci.model.Pipeline;
import com.yourname.streamci.streamci.service.BuildService;
import com.yourname.streamci.streamci.service.PipelineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
public class BuildController {

    private final BuildService buildService;
    private final PipelineService pipelineService;
    private final DtoMapper dtoMapper;

    public BuildController(BuildService buildService,
                          PipelineService pipelineService,
                          DtoMapper dtoMapper){
        this.buildService = buildService;
        this.pipelineService = pipelineService;
        this.dtoMapper = dtoMapper;
    }

    @GetMapping("/api/builds")
    public ResponseEntity<List<BuildResponse>> printBuilds(){
        List<Build> builds = buildService.getAllBuilds();
        List<BuildResponse> response = dtoMapper.toBuildResponseList(builds);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/builds/{id}")
    public ResponseEntity<BuildResponse> getBuild(@PathVariable Long id){
        Optional<Build> result = buildService.getBuildById(id);
        if (result.isPresent()){
            BuildResponse response = dtoMapper.toBuildResponse(result.get());
            return ResponseEntity.ok(response);
        } else{
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/api/pipelines/{pipelineId}/builds")
    public ResponseEntity<List<BuildResponse>> getBuildByPipelineId(@PathVariable Integer pipelineId){
        List<Build> builds = buildService.getBuildsByPipelineId(pipelineId);
        if (builds.isEmpty()){
            return ResponseEntity.notFound().build();
        } else{
            List<BuildResponse> response = dtoMapper.toBuildResponseList(builds);
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/api/builds")
    public ResponseEntity<?> addBuild(@Valid @RequestBody CreateBuildRequest request){
        // validation is now handled by @Valid annotation

        // lookup pipeline
        Optional<Pipeline> pipeline = pipelineService.getPipelineById(request.getPipelineId());
        if (pipeline.isEmpty()) {
            return ResponseEntity.badRequest().body("Pipeline not found with id: " + request.getPipelineId());
        }

        Build build = dtoMapper.toBuild(request, pipeline.get());
        Build savedBuild = buildService.saveBuild(build);
        BuildResponse response = dtoMapper.toBuildResponse(savedBuild);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/api/builds/{buildId}")
    public ResponseEntity<BuildResponse> updateBuild(@PathVariable Long buildId,
                                                     @Valid @RequestBody UpdateBuildRequest request){
        Optional<Build> existingBuild = buildService.getBuildById(buildId);
        if (existingBuild.isEmpty()){
            return ResponseEntity.notFound().build();
        }

        Build build = existingBuild.get();
        dtoMapper.updateBuildFromRequest(build, request);
        Build updatedBuild = buildService.saveBuild(build);
        BuildResponse response = dtoMapper.toBuildResponse(updatedBuild);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/api/builds/{buildId}")
    public ResponseEntity<Void> deleteBuild(@PathVariable Long buildId){
        boolean isDeleted = buildService.deleteBuild(buildId);
        if (isDeleted){
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
