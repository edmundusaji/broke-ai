package org.edmund.brokeai.controller;

import org.edmund.brokeai.config.OfflineSyncProperties;
import org.edmund.brokeai.dto.SyncApi;
import org.edmund.brokeai.exception.ApiExceptionHandler;
import org.edmund.brokeai.service.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SyncControllerTest {
    @Mock private SyncService syncService;

    private OfflineSyncProperties properties;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        properties = new OfflineSyncProperties();
        mockMvc = MockMvcBuilders.standaloneSetup(new SyncController(syncService, properties))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @Test
    void pushIsAvailableByDefaultAndReturnsApplied() throws Exception {
        UUID operationId = UUID.randomUUID();
        when(syncService.push(any())).thenReturn(new SyncApi.PushResponse(
            List.of(new SyncApi.OperationResult(
                operationId,
                SyncApi.OperationStatus.APPLIED,
                null,
                null,
                null
            )),
            12L
        ));

        mockMvc.perform(post("/api/v1/sync/push")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "deviceId": "%s",
                      "operations": [{
                        "operationId": "%s",
                        "type": "CREATE",
                        "clientTransactionId": "%s",
                        "transaction": {
                          "date": "2026-08-26",
                          "amount": 25000,
                          "category": "Food"
                        }
                      }]
                    }
                    """.formatted(UUID.randomUUID(), operationId, UUID.randomUUID())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results[0].status").value("APPLIED"))
            .andExpect(jsonPath("$.serverRevision").value(12));

        verify(syncService).push(any());
    }

    @Test
    void explicitlyDisabledSyncStillReturnsServiceUnavailable() throws Exception {
        properties.setEnabled(false);

        mockMvc.perform(get("/api/v1/sync/pull"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code").value("SYNC_FEATURE_DISABLED"));

        verify(syncService, never()).pull(any(), any(Integer.class));
    }
}
