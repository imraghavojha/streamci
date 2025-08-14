package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.Pipeline;
import com.yourname.streamci.streamci.repository.BuildRepository;
import com.yourname.streamci.streamci.repository.PipelineRepository;
import org.springframework.stereotype.Service;
import com.yourname.streamci.streamci.model.Build;
import java.util.List;
import java.util.Optional;


@Service
public class BuildService {

    PipelineRepository pipelineRepository;
    BuildRepository buildRepository;

    public BuildService(BuildRepository buildRepository, PipelineRepository pipelineRepository){
        this.buildRepository = buildRepository;
        this.pipelineRepository = pipelineRepository;
    }

    public List<Build> getAllBuilds(){
        return buildRepository.findAll();
    }

    public Optional<Build> getBuildById(Long buildId){
        return buildRepository.findById(buildId);
    }

    public List<Build> getBuildsByPipelineId(Integer pipelineId) {
        return buildRepository.findByPipelineId(pipelineId);
    }

    public Build saveBuild(Build build){
        return buildRepository.save(build);
    }

    public boolean deleteBuild(Long buildId){
        if (buildRepository.existsById(buildId)){
            buildRepository.deleteById(buildId);
            return true;
        } else {
            return false;
        }
    }

    public Optional<Build> updateBuild(Long buildId, Build updates) {
        return buildRepository.findById(buildId)
                .map(existing -> {
                    existing.setStatus(updates.getStatus());
                    existing.setStartTime(updates.getStartTime());
                    existing.setEndTime(updates.getEndTime());
                    existing.setDuration(updates.getDuration());
                    existing.setCommitHash(updates.getCommitHash());
                    existing.setCommitter(updates.getCommitter());
                    existing.setBranch(updates.getBranch());

                    return buildRepository.save(existing);
                });
    }
}
