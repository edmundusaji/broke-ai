package org.edmund.brokeai.service.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.PageEnvelope;
import org.edmund.brokeai.dto.ProfileSettingsApi;
import org.edmund.brokeai.entity.FaqArticle;
import org.edmund.brokeai.entity.SupportTicket;
import org.edmund.brokeai.exception.ApiException;
import org.edmund.brokeai.repository.FaqArticleRepository;
import org.edmund.brokeai.repository.SupportTicketRepository;
import org.edmund.brokeai.security.CurrentUserService;
import org.edmund.brokeai.security.SensitiveValueCipher;
import org.edmund.brokeai.service.SupportService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class SupportServiceImpl implements SupportService {
    private static final Set<String> DIAGNOSTIC_KEYS = Set.of(
        "buildNumber", "screenWidth", "screenHeight", "networkType", "isPhysicalDevice"
    );
    private final CurrentUserService currentUserService;
    private final FaqArticleRepository faqArticleRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final SensitiveValueCipher sensitiveValueCipher;

    @Override
    @Transactional(readOnly = true)
    public PageEnvelope<ProfileSettingsApi.FaqArticle> getFaqs(
        String locale, String category, int page, int size
    ) {
        var pageable = PageRequest.of(page, Math.min(size, 100));
        var result = category == null || category.isBlank()
            ? faqArticleRepository.findByLocaleAndPublicationStatusOrderByDisplayOrder(locale, "published", pageable)
            : faqArticleRepository.findByLocaleAndCategoryAndPublicationStatusOrderByDisplayOrder(
                locale, category, "published", pageable
            );
        return PageEnvelope.of(result.stream().map(this::map).toList(), page, Math.min(size, 100), result.getTotalElements());
    }

    @Override
    @Transactional
    public ProfileSettingsApi.SupportTicket createTicket(ProfileSettingsApi.SupportTicketRequest request) {
        SupportTicket ticket = new SupportTicket();
        ticket.setUser(currentUserService.getCurrentUser());
        ticket.setType(request.type());
        ticket.setSubject(request.subject().trim());
        ticket.setMessage(request.message().trim());
        ticket.setAppVersion(request.appVersion());
        ticket.setPlatform(request.platform());
        ticket.setOsVersion(request.osVersion());
        ticket.setDeviceModel(request.deviceModel());
        ticket.setLocale(request.locale());
        ticket.setCurrentRoute(request.currentRoute());
        ticket.setDiagnosticMetadata(sanitizeDiagnostics(request.diagnosticMetadata()));
        setOptionalContact(ticket, request.contactEmail(), request.contactConsent());
        return map(supportTicketRepository.save(ticket));
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileSettingsApi.SupportTicket getTicket(UUID ticketId) {
        Long userId = currentUserService.getCurrentUser().getId();
        return map(supportTicketRepository.findByIdAndUserId(ticketId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Support ticket not found.")));
    }

    private ProfileSettingsApi.FaqArticle map(FaqArticle value) {
        return new ProfileSettingsApi.FaqArticle(
            value.getId(), value.getLocale(), value.getCategory(), value.getTitle(), value.getBody(), value.getDisplayOrder()
        );
    }

    private ProfileSettingsApi.SupportTicket map(SupportTicket value) {
        return new ProfileSettingsApi.SupportTicket(
            value.getId(), value.getType(), value.getSubject(), value.getMessage(), value.getStatus(),
            value.getCreatedAt(), value.getUpdatedAt(), value.getResolvedAt()
        );
    }

    private Map<String, Object> sanitizeDiagnostics(Map<String, Object> supplied) {
        if (supplied == null || supplied.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        supplied.forEach((key, value) -> {
            if (DIAGNOSTIC_KEYS.contains(key) && (value instanceof Number || value instanceof Boolean || value instanceof String)) {
                String text = String.valueOf(value);
                result.put(key, text.length() <= 200 ? value : text.substring(0, 200));
            }
        });
        return result;
    }

    private void setOptionalContact(SupportTicket ticket, String contactEmail, Boolean contactConsent) {
        if (contactEmail == null || contactEmail.isBlank()) {
            if (Boolean.TRUE.equals(contactConsent)) {
                throw ServiceSupport.validation("contactEmail", "A contact email is required when consent is given.");
            }
            return;
        }
        if (!Boolean.TRUE.equals(contactConsent)) {
            throw ServiceSupport.validation("contactConsent", "Consent is required to store a contact email.");
        }
        ticket.setContactEmailEncrypted(sensitiveValueCipher.encrypt(contactEmail.trim().toLowerCase(java.util.Locale.ROOT)));
        ticket.setContactConsentAt(Instant.now());
    }
}
