package com.yourname.streamci.streamci.repository;

import com.yourname.streamci.streamci.model.AlertConfig;
import com.yourname.streamci.streamci.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AlertConfigRepository extends JpaRepository<AlertConfig, Long> {

    Optional<AlertConfig> findByPipelineIdAndAlertType(
            Integer pipelineId,
            Alert.AlertType alertType
    );

    Optional<AlertConfig> findByPipelineIsNullAndAlertType(
            Alert.AlertType alertType
    );

    List<AlertConfig> findByEnabledTrue();

    List<AlertConfig> findByPipelineIdAndEnabledTrue(Integer pipelineId);
}