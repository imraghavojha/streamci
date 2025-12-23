package com.yourname.streamci.streamci.repository;

import com.yourname.streamci.streamci.model.Build;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BuildRepository extends JpaRepository<Build, Long> {
    List<Build> findByPipelineId(Integer pipelineId);

    @Query("SELECT b FROM Build b WHERE b.pipeline.id = :pipelineId " +
            "AND b.startTime BETWEEN :startDate AND :endDate " +
            "AND b.status = 'failure' ORDER BY b.startTime DESC")
    List<Build> findFailuresByTimePattern(@Param("pipelineId") Integer pipelineId,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    @Query("SELECT b FROM Build b WHERE b.pipeline.id = :pipelineId " +
            "AND b.committer = :committer AND b.status = 'failure' " +
            "ORDER BY b.startTime DESC")
    List<Build> findFailuresByCommitter(@Param("pipelineId") Integer pipelineId,
                                        @Param("committer") String committer);

    @Query("SELECT HOUR(b.startTime) as hour, " +
            "COUNT(b) as total, " +
            "SUM(CASE WHEN b.status = 'success' THEN 1 ELSE 0 END) as successes " +
            "FROM Build b WHERE b.pipeline.id = :pipelineId " +
            "AND b.startTime IS NOT NULL " +
            "GROUP BY HOUR(b.startTime)")
    List<Object[]> calculateSuccessRateByHour(@Param("pipelineId") Integer pipelineId);

    @Query("SELECT b FROM Build b WHERE b.pipeline.id = :pipelineId " +
            "AND b.branch = :branch AND b.committer = :committer " +
            "ORDER BY b.startTime DESC LIMIT :limit")
    List<Build> findRecentBuildsByPattern(@Param("pipelineId") Integer pipelineId,
                                          @Param("branch") String branch,
                                          @Param("committer") String committer,
                                          @Param("limit") Integer limit);

    @Query("SELECT b.committer, COUNT(b) as total, " +
            "SUM(CASE WHEN b.status = 'success' THEN 1 ELSE 0 END) as successes " +
            "FROM Build b WHERE b.pipeline.id = :pipelineId " +
            "AND b.committer IS NOT NULL AND b.committer != 'unknown' " +
            "GROUP BY b.committer HAVING COUNT(b) >= 3")
    List<Object[]> findCommitterSuccessRates(@Param("pipelineId") Integer pipelineId);

    @Query("SELECT b.branch, COUNT(b) as total, " +
            "SUM(CASE WHEN b.status = 'success' THEN 1 ELSE 0 END) as successes " +
            "FROM Build b WHERE b.pipeline.id = :pipelineId " +
            "AND b.branch IS NOT NULL " +
            "GROUP BY b.branch HAVING COUNT(b) >= 3")
    List<Object[]> findBranchSuccessRates(@Param("pipelineId") Integer pipelineId);

    // performance optimization: query builds by date range instead of loading all
    @Query("SELECT b FROM Build b WHERE b.startTime >= :startDate ORDER BY b.startTime DESC")
    List<Build> findByStartTimeAfter(@Param("startDate") LocalDateTime startDate);

    // performance optimization: query builds by date range for specific pipeline
    @Query("SELECT b FROM Build b WHERE b.pipeline.id = :pipelineId " +
            "AND b.startTime >= :startDate ORDER BY b.startTime DESC")
    List<Build> findByPipelineIdAndStartTimeAfter(@Param("pipelineId") Integer pipelineId,
                                                   @Param("startDate") LocalDateTime startDate);

    // performance optimization: query builds by date range with status filter
    @Query("SELECT b FROM Build b WHERE b.startTime >= :startDate " +
            "AND b.status = :status ORDER BY b.startTime DESC")
    List<Build> findByStartTimeAfterAndStatus(@Param("startDate") LocalDateTime startDate,
                                               @Param("status") String status);

    // performance optimization: count builds by status in date range
    @Query("SELECT COUNT(b) FROM Build b WHERE b.startTime >= :startDate AND b.status = :status")
    long countByStartTimeAfterAndStatus(@Param("startDate") LocalDateTime startDate,
                                        @Param("status") String status);
}
