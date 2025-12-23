package com.yourname.streamci.streamci.dto;

import com.yourname.streamci.streamci.dto.request.CreateBuildRequest;
import com.yourname.streamci.streamci.dto.request.CreatePipelineRequest;
import com.yourname.streamci.streamci.dto.request.UpdateBuildRequest;
import com.yourname.streamci.streamci.dto.request.UpdatePipelineRequest;
import com.yourname.streamci.streamci.dto.response.BuildResponse;
import com.yourname.streamci.streamci.dto.response.PipelineResponse;
import com.yourname.streamci.streamci.model.Build;
import com.yourname.streamci.streamci.model.Pipeline;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * utility class for mapping between entities and dtos
 * keeps controllers clean and ensures consistent mapping logic
 */
@Component
public class DtoMapper {

    // ========== BUILD MAPPINGS ==========

    public BuildResponse toBuildResponse(Build build) {
        if (build == null) {
            return null;
        }
        return BuildResponse.builder()
                .buildId(build.getBuildId())
                .pipelineId(build.getPipeline() != null ? build.getPipeline().getId() : null)
                .status(build.getStatus())
                .createdAt(build.getCreatedAt())
                .updatedAt(build.getUpdatedAt())
                .startTime(build.getStartTime())
                .endTime(build.getEndTime())
                .duration(build.getDuration())
                .commitHash(build.getCommitHash())
                .committer(build.getCommitter())
                .branch(build.getBranch())
                .build();
    }

    public List<BuildResponse> toBuildResponseList(List<Build> builds) {
        if (builds == null) {
            return null;
        }
        return builds.stream()
                .map(this::toBuildResponse)
                .collect(Collectors.toList());
    }

    public Build toBuild(CreateBuildRequest request, Pipeline pipeline) {
        if (request == null) {
            return null;
        }
        return Build.builder()
                .pipeline(pipeline)
                .status(request.getStatus())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .duration(request.getDuration())
                .commitHash(request.getCommitHash())
                .committer(request.getCommitter())
                .branch(request.getBranch())
                .build();
    }

    public void updateBuildFromRequest(Build build, UpdateBuildRequest request) {
        if (request == null) {
            return;
        }
        if (request.getStatus() != null) {
            build.setStatus(request.getStatus());
        }
        if (request.getStartTime() != null) {
            build.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            build.setEndTime(request.getEndTime());
        }
        if (request.getDuration() != null) {
            build.setDuration(request.getDuration());
        }
        if (request.getCommitHash() != null) {
            build.setCommitHash(request.getCommitHash());
        }
        if (request.getCommitter() != null) {
            build.setCommitter(request.getCommitter());
        }
        if (request.getBranch() != null) {
            build.setBranch(request.getBranch());
        }
    }

    // ========== PIPELINE MAPPINGS ==========

    public PipelineResponse toPipelineResponse(Pipeline pipeline) {
        if (pipeline == null) {
            return null;
        }
        return PipelineResponse.builder()
                .id(pipeline.getId())
                .name(pipeline.getName())
                .status(pipeline.getStatus())
                .duration(pipeline.getDuration())
                .createdAt(pipeline.getCreatedAt())
                .updatedAt(pipeline.getUpdatedAt())
                .builds(toBuildResponseList(pipeline.getBuilds()))
                .build();
    }

    public List<PipelineResponse> toPipelineResponseList(List<Pipeline> pipelines) {
        if (pipelines == null) {
            return null;
        }
        return pipelines.stream()
                .map(this::toPipelineResponse)
                .collect(Collectors.toList());
    }

    public Pipeline toPipeline(CreatePipelineRequest request) {
        if (request == null) {
            return null;
        }
        return Pipeline.builder()
                .name(request.getName())
                .status(request.getStatus())
                .duration(request.getDuration() != null ? request.getDuration() : 0)
                .build();
    }

    public void updatePipelineFromRequest(Pipeline pipeline, UpdatePipelineRequest request) {
        if (request == null) {
            return;
        }
        if (request.getName() != null) {
            pipeline.setName(request.getName());
        }
        if (request.getStatus() != null) {
            pipeline.setStatus(request.getStatus());
        }
        if (request.getDuration() != null) {
            pipeline.setDuration(request.getDuration());
        }
    }
}
