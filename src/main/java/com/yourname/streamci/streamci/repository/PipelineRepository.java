package com.yourname.streamci.streamci.repository;
import com.yourname.streamci.streamci.model.Pipeline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PipelineRepository extends JpaRepository<Pipeline, Integer> {
}
