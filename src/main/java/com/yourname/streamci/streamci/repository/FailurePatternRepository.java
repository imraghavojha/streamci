package com.yourname.streamci.streamci.repository;

import com.yourname.streamci.streamci.model.FailurePattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FailurePatternRepository extends JpaRepository<FailurePattern, Long> {

    List<FailurePattern> findByPipelineIdOrderByConfidenceDesc(Integer pipelineId);

    List<FailurePattern> findByPipelineIdAndPatternTypeOrderByConfidenceDesc(
            Integer pipelineId, String patternType);

    @Query("SELECT fp FROM FailurePattern fp WHERE fp.pipeline.id = :pipelineId " +
            "AND fp.detectedAt > :since ORDER BY fp.confidence DESC")
    List<FailurePattern> findRecentPatterns(@Param("pipelineId") Integer pipelineId,
                                            @Param("since") LocalDateTime since);

    Optional<FailurePattern> findByPipelineIdAndPatternTypeAndDescription(
            Integer pipelineId, String patternType, String description);

    @Query("SELECT DISTINCT fp.patternType FROM FailurePattern fp WHERE fp.pipeline.id = :pipelineId")
    List<String> findDistinctPatternTypes(@Param("pipelineId") Integer pipelineId);

    void deleteByPipelineIdAndDetectedAtBefore(Integer pipelineId, LocalDateTime before);
}