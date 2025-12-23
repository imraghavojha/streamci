package com.yourname.streamci.streamci.service;

import com.yourname.streamci.streamci.model.Pipeline;
import com.yourname.streamci.streamci.repository.PipelineRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PipelineService extends AbstractCrudService<Pipeline, Integer> {

    public PipelineService(PipelineRepository pipelineRepository) {
        super(pipelineRepository);
    }

    // update method with pipeline-specific logic
    public Optional<Pipeline> updatePipeline(Integer id, Pipeline updates) {
        return update(id, existing -> {
            existing.setName(updates.getName());
            existing.setStatus(updates.getStatus());
            existing.setDuration(updates.getDuration());
        });
    }

    // convenience methods that delegate to parent
    public List<Pipeline> getAllPipelines() {
        return getAll();
    }

    public Optional<Pipeline> getPipelineById(Integer id) {
        return getById(id);
    }

    public Pipeline savePipeline(Pipeline pipeline) {
        return save(pipeline);
    }

    public boolean deletePipeline(Integer id) {
        return delete(id);
    }
}
