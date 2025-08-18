package com.yourname.streamci.streamci.config;

import com.yourname.streamci.streamci.model.Alert;
import com.yourname.streamci.streamci.model.AlertConfig;
import com.yourname.streamci.streamci.repository.AlertConfigRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class AlertConfigInitializer {

    private static final Logger logger = LoggerFactory.getLogger(AlertConfigInitializer.class);

    @Bean
    CommandLineRunner initAlertConfigs(AlertConfigRepository repository) {
        return args -> {
            // check if configs already exist
            if (repository.count() > 0) {
                logger.info("Alert configurations already exist, skipping initialization");
                return;
            }

            logger.info("Initializing default alert configurations");

            // success rate alert
            AlertConfig successRate = AlertConfig.builder()
                    .pipeline(null) // global config
                    .alertType(Alert.AlertType.SUCCESS_RATE_DROP)
                    .enabled(true)
                    .warningThreshold(75.0)
                    .criticalThreshold(50.0)
                    .evaluationWindowMinutes(30)
                    .cooldownMinutes(15)
                    .build();
            repository.save(successRate);

            // duration increase alert
            AlertConfig duration = AlertConfig.builder()
                    .pipeline(null)
                    .alertType(Alert.AlertType.DURATION_INCREASE)
                    .enabled(true)
                    .warningThreshold(50.0)  // 50% increase
                    .criticalThreshold(100.0) // 100% increase
                    .evaluationWindowMinutes(60)
                    .cooldownMinutes(30)
                    .build();
            repository.save(duration);

            // consecutive failures alert
            AlertConfig consecutive = AlertConfig.builder()
                    .pipeline(null)
                    .alertType(Alert.AlertType.CONSECUTIVE_FAILURES)
                    .enabled(true)
                    .warningThreshold(3.0)
                    .criticalThreshold(5.0)
                    .evaluationWindowMinutes(120)
                    .cooldownMinutes(60)
                    .build();
            repository.save(consecutive);

            // stale pipeline alert
            AlertConfig stale = AlertConfig.builder()
                    .pipeline(null)
                    .alertType(Alert.AlertType.STALE_PIPELINE)
                    .enabled(true)
                    .warningThreshold(24.0)  // hours
                    .criticalThreshold(72.0) // hours
                    .evaluationWindowMinutes(1440) // 24 hours
                    .cooldownMinutes(720) // 12 hours
                    .build();
            repository.save(stale);

            logger.info("Created {} default alert configurations", repository.count());
        };
    }
}