package org.edmund.brokeai.service;

import org.edmund.brokeai.dto.ProfileSettingsApi;

import java.util.UUID;

public interface DataControlService {
    ProfileSettingsApi.DataExport requestExport(String idempotencyKey);

    ProfileSettingsApi.DataExport getExport(UUID jobId);

    byte[] downloadExport(UUID jobId, String token);

    ProfileSettingsApi.ClearTransactions clearTransactions(
        String idempotencyKey,
        ProfileSettingsApi.ClearTransactionsRequest request
    );

    ProfileSettingsApi.AccountDeletion requestDeletion(
        String idempotencyKey,
        ProfileSettingsApi.AccountDeletionRequest request
    );

    void cancelDeletion();
}
