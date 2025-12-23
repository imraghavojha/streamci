package com.yourname.streamci.streamci.repository;

import com.yourname.streamci.streamci.model.QueueMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface QueueMetricsRepository extends JpaRepository<QueueMetrics, Long> {

    List<QueueMetrics> findByPipelineIdAndTimestampBetweenOrderByTimestampAsc(
            Integer pipelineId,
            LocalDateTime start,
            LocalDateTime end
    );

    Optional<QueueMetrics> findTopByPipelineIdOrderByTimestampDesc(Integer pipelineId);

    @Query("SELECT AVG(q.currentQueueDepth) FROM QueueMetrics q " +
            "WHERE q.pipeline.id = :pipelineId AND q.timestamp > :since")
    Double getAverageQueueDepth(@Param("pipelineId") Integer pipelineId,
                                @Param("since") LocalDateTime since);

    // performance optimization: query by date range instead of loading all
    @Query("SELECT q FROM QueueMetrics q WHERE q.timestamp >= :startDate ORDER BY q.timestamp ASC")
    List<QueueMetrics> findByTimestampAfter(@Param("startDate") LocalDateTime startDate);
}
