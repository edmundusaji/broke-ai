package org.edmund.brokeai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.offline-sync")
@Slf4j
public class OfflineSyncProperties implements ApplicationRunner {
    private boolean enabled = true;
    private boolean requireEnabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRequireEnabled() {
        return requireEnabled;
    }

    public void setRequireEnabled(boolean requireEnabled) {
        this.requireEnabled = requireEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Offline transaction sync enabled: {}", enabled);

        if (requireEnabled && !enabled) {
            throw new IllegalStateException(
                "Offline transaction sync is required in this environment but is disabled."
            );
        }
    }
}
