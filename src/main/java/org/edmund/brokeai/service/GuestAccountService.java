package org.edmund.brokeai.service;

import org.edmund.brokeai.dto.ProfileSettingsApi;

public interface GuestAccountService {
    ProfileSettingsApi.ClearTransactions clearTransactions(
        String idempotencyKey,
        ProfileSettingsApi.GuestConfirmationRequest request
    );

    ProfileSettingsApi.GuestDeletion deleteGuest(
        String idempotencyKey,
        ProfileSettingsApi.GuestConfirmationRequest request
    );

    ProfileSettingsApi.GuestDataSummary getDataSummary();
}
