package com.yourname.streamci.streamci.repository;

import com.yourname.streamci.streamci.model.QueueTracker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface QueueTrackerRepository extends JpaRepository<QueueTracker, Long> {

    Optional<QueueTracker> findByBuildId(String buildId);

    List<QueueTracker> findByPipelineIdAndStatus(Integer pipelineId, String status);

    @Query("SELECT COUNT(q) FROM QueueTracker q WHERE q.pipeline.id = :pipelineId " +
            "AND q.status IN ('queued', 'running')")
    Integer countActiveBuilds(@Param("pipelineId") Integer pipelineId);

    @Query("SELECT AVG(q.waitTimeSeconds) FROM QueueTracker q " +
            "WHERE q.pipeline.id = :pipelineId AND q.completedAt > :since")
    Double getAverageWaitTime(@Param("pipelineId") Integer pipelineId,
                              @Param("since") LocalDateTime since);
}