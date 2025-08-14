package com.yourname.streamci.streamci.repository;

import com.yourname.streamci.streamci.model.Build;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BuildRepository extends JpaRepository<Build, Long> {
    List<Build> findByPipelineId(Integer pipelineId);
}
