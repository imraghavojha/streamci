package com.yourname.streamci.streamci.repository;

import com.yourname.streamci.streamci.model.PipelineMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PipelineMetricsRepository extends JpaRepository<PipelineMetrics, Long> {

    Optional<PipelineMetrics> findTopByPipelineIdOrderByCalculatedAtDesc(Integer pipelineId);

    List<PipelineMetrics> findByPipelineIdAndCalculatedAtBetween(
            Integer pipelineId,
            LocalDateTime start,
            LocalDateTime end
    );

    @Query("SELECT pm FROM PipelineMetrics pm WHERE pm.pipeline.id = :pipelineId " +
            "AND pm.calculatedAt = (SELECT MAX(pm2.calculatedAt) FROM PipelineMetrics pm2 " +
            "WHERE pm2.pipeline.id = :pipelineId)")
    Optional<PipelineMetrics> findLatestByPipelineId(@Param("pipelineId") Integer pipelineId);

    // performance optimization: query by date range instead of loading all
    @Query("SELECT pm FROM PipelineMetrics pm WHERE pm.calculatedAt >= :startDate ORDER BY pm.calculatedAt ASC")
    List<PipelineMetrics> findByCalculatedAtAfter(@Param("startDate") LocalDateTime startDate);
}