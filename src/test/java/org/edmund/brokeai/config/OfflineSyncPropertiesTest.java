package org.edmund.brokeai.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class OfflineSyncPropertiesTest {

    @Test
    void defaultsToEnabledAndLogsEffectiveStatus(CapturedOutput output) {
        OfflineSyncProperties properties = new OfflineSyncProperties();

        assertDoesNotThrow(() -> properties.run(null));

        assertTrue(properties.isEnabled());
        assertTrue(output.getOut().contains("Offline transaction sync enabled: true"));
    }

    @Test
    void requiredEnvironmentFailsStartupWhenSyncIsDisabled() {
        OfflineSyncProperties properties = new OfflineSyncProperties();
        properties.setEnabled(false);
        properties.setRequireEnabled(true);

        assertThrows(IllegalStateException.class, () -> properties.run(null));
    }
}
