package com.yourname.streamci.streamci.controller;


import com.yourname.streamci.streamci.model.Build;
import com.yourname.streamci.streamci.service.BuildService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
public class BuildController {

    private final BuildService buildService;

    public BuildController(BuildService buildService){
        this.buildService = buildService;
    }

    @GetMapping("/api/builds")
    public ResponseEntity<List<Build>> printBuilds(){
        List<Build> result = buildService.getAllBuilds();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/builds/{id}")
    public ResponseEntity<Build> getBuild(@PathVariable Long id){
        Optional<Build> result = buildService.getBuildById(id);
        if (result.isPresent()){
            return ResponseEntity.ok(result.get());
        } else{
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/api/pipelines/{pipelineId}/builds")
    public ResponseEntity<List<Build>> getBuildByPipelineId(@PathVariable Integer pipelineId){
        List<Build> result = buildService.getBuildsByPipelineId(pipelineId);
        if (result.isEmpty()){
            return ResponseEntity.notFound().build();
        } else{
            return ResponseEntity.ok(result);
        }
    }

    @PostMapping("/api/builds")
    public ResponseEntity<?> addBuild(@RequestBody Build build){
        if (build.getStatus() == null || build.getStatus().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Build status is required");
        }
        Build savedBuild = buildService.saveBuild(build);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedBuild);
    }

    @PutMapping("/api/builds/{buildId}")
    public ResponseEntity<Build> updateBuild(@PathVariable Long buildId, @RequestBody Build build){
        Optional<Build> updatedBuild = buildService.updateBuild(buildId, build);
        if (updatedBuild.isEmpty()){
            return ResponseEntity.notFound().build();
        } else{
            return ResponseEntity.ok(updatedBuild.get());
        }
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
