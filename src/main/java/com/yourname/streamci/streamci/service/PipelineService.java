package com.yourname.streamci.streamci.service;
import com.yourname.streamci.streamci.model.Pipeline;
import com.yourname.streamci.streamci.repository.PipelineRepository;
import java.util.List;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PipelineService {


    PipelineRepository pipelineRepository;


    public PipelineService(PipelineRepository  pipelineRepository){
        this.pipelineRepository = pipelineRepository;
    }

    public List<Pipeline> getAllPipelines(){
        return pipelineRepository.findAll();
    }

    public Optional<Pipeline> getPipelineById(Integer id){
        return pipelineRepository.findById(id);
    }

    public Pipeline savePipeline(Pipeline pipeline){
        return pipelineRepository.save(pipeline);
    }

    public boolean deletePipeline(Integer id){
        if (pipelineRepository.existsById(id)){
            pipelineRepository.deleteById(id);
            return true;
        } else {
            return false;
        }
    }

    public Optional<Pipeline> updatePipeline(Integer id, Pipeline updates){
        return pipelineRepository.findById(id)
                .map(existing -> {
                    existing.setName(updates.getName());
                    existing.setStatus(updates.getStatus());
                    existing.setDuration(updates.getDuration());
                    return pipelineRepository.save(existing);
                });
    }
}
