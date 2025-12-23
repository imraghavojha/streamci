package com.yourname.streamci.streamci.repository;

import com.yourname.streamci.streamci.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByStatusOrderByCreatedAtDesc(Alert.AlertStatus status);

    List<Alert> findByPipelineIdAndStatusOrderByCreatedAtDesc(
            Integer pipelineId,
            Alert.AlertStatus status
    );

    @Query("SELECT a FROM Alert a WHERE a.pipeline.id = :pipelineId " +
            "AND a.status = 'ACTIVE' AND a.severity IN ('CRITICAL', 'EMERGENCY')")
    List<Alert> findCriticalAlertsByPipeline(@Param("pipelineId") Integer pipelineId);

    @Query("SELECT a FROM Alert a WHERE a.fingerprint = :fingerprint " +
            "AND a.status = 'ACTIVE'")
    Optional<Alert> findActiveByFingerprint(@Param("fingerprint") String fingerprint);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.pipeline.id = :pipelineId " +
            "AND a.createdAt > :since AND a.status = 'ACTIVE'")
    long countRecentAlerts(@Param("pipelineId") Integer pipelineId,
                           @Param("since") LocalDateTime since);

    List<Alert> findByStatusAndCreatedAtBefore(
            Alert.AlertStatus status,
            LocalDateTime before
    );

    // performance optimization: count alerts by date range instead of loading all
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.createdAt >= :startDate")
    long countByCreatedAtAfter(@Param("startDate") LocalDateTime startDate);
}