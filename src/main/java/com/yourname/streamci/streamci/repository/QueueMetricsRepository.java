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
}
