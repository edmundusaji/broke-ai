package org.edmund.brokeai.service;

import org.edmund.brokeai.dto.PageEnvelope;
import org.edmund.brokeai.dto.ProfileSettingsApi;

import java.util.UUID;

public interface SupportService {
    PageEnvelope<ProfileSettingsApi.FaqArticle> getFaqs(String locale, String category, int page, int size);

    ProfileSettingsApi.SupportTicket createTicket(ProfileSettingsApi.SupportTicketRequest request);

    ProfileSettingsApi.SupportTicket getTicket(UUID ticketId);
}
