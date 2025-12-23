package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.Build;
import com.yourname.streamci.streamci.repository.BuildRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BuildService extends AbstractCrudService<Build, Long> {

    private final BuildRepository buildRepository;

    public BuildService(BuildRepository buildRepository) {
        super(buildRepository);
        this.buildRepository = buildRepository;
    }

    // custom query methods specific to builds
    public List<Build> getBuildsByPipelineId(Integer pipelineId) {
        return buildRepository.findByPipelineId(pipelineId);
    }

    // update method with build-specific logic
    public Optional<Build> updateBuild(Long buildId, Build updates) {
        return update(buildId, existing -> {
            existing.setStatus(updates.getStatus());
            existing.setStartTime(updates.getStartTime());
            existing.setEndTime(updates.getEndTime());
            existing.setDuration(updates.getDuration());
            existing.setCommitHash(updates.getCommitHash());
            existing.setCommitter(updates.getCommitter());
            existing.setBranch(updates.getBranch());
        });
    }

    // convenience methods that delegate to parent
    public List<Build> getAllBuilds() {
        return getAll();
    }

    public Optional<Build> getBuildById(Long buildId) {
        return getById(buildId);
    }

    public Build saveBuild(Build build) {
        return save(build);
    }

    public boolean deleteBuild(Long buildId) {
        return delete(buildId);
    }
}
