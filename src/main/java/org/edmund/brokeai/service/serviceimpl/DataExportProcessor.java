package org.edmund.brokeai.service.serviceimpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.entity.DataExportJob;
import org.edmund.brokeai.entity.Transaction;
import org.edmund.brokeai.entity.NotificationPreference;
import org.edmund.brokeai.entity.PrivacyPreference;
import org.edmund.brokeai.entity.UserPreference;
import org.edmund.brokeai.repository.DataExportJobRepository;
import org.edmund.brokeai.repository.NotificationPreferenceRepository;
import org.edmund.brokeai.repository.PrivacyPreferenceRepository;
import org.edmund.brokeai.repository.TransactionRepository;
import org.edmund.brokeai.repository.UserPreferenceRepository;
import org.edmund.brokeai.service.PrivateObjectStorage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
@RequiredArgsConstructor
public class DataExportProcessor {
    private final DataExportJobRepository dataExportJobRepository;
    private final TransactionRepository transactionRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final PrivacyPreferenceRepository privacyPreferenceRepository;
    private final PrivateObjectStorage objectStorage;
    private final ObjectMapper objectMapper;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(DataExportRequestedEvent event) {
        DataExportJob job = dataExportJobRepository.findById(event.jobId()).orElse(null);
        if (job == null || !"queued".equals(job.getStatus())) return;
        try {
            job.setStatus("processing");
            job.setUpdatedAt(Instant.now());
            dataExportJobRepository.saveAndFlush(job);

            List<Transaction> transactions = transactionRepository
                .findByUserIdAndDeletedAtIsNullOrderByDateDesc(job.getUser().getId());
            byte[] archive = createArchive(job, transactions);
            String objectKey = "exports/" + job.getUser().getId() + "/" + job.getId() + ".zip";
            objectStorage.put(objectKey, archive);
            Instant now = Instant.now();
            job.setObjectKey(objectKey);
            job.setStatus("ready");
            job.setCompletedAt(now);
            job.setExpiresAt(now.plus(Duration.ofHours(24)));
            job.setUpdatedAt(now);
        } catch (Exception exception) {
            job.setStatus("failed");
            job.setFailureReason("The export could not be generated.");
            job.setUpdatedAt(Instant.now());
        }
        dataExportJobRepository.save(job);
    }

    @Scheduled(fixedDelayString = "${app.data-export.cleanup-ms:3600000}")
    @Transactional
    public void removeExpiredExports() {
        for (DataExportJob job : dataExportJobRepository.findByStatusAndExpiresAtBefore("ready", Instant.now())) {
            if (job.getObjectKey() != null) objectStorage.delete(job.getObjectKey());
            job.setStatus("expired");
            job.setObjectKey(null);
            job.setUpdatedAt(Instant.now());
            dataExportJobRepository.save(job);
        }
    }

    private byte[] createArchive(DataExportJob job, List<Transaction> transactions) throws Exception {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", job.getUser().getId());
        profile.put("fullName", job.getUser().getFullName());
        profile.put("username", job.getUser().getUsername());
        profile.put("email", job.getUser().getEmail());
        profile.put("emailVerifiedAt", job.getUser().getEmailVerifiedAt());
        profile.put("phone", job.getUser().getPhone());
        profile.put("status", job.getUser().getStatus());
        profile.put("createdAt", job.getUser().getCreatedAt());
        profile.put("updatedAt", job.getUser().getUpdatedAt());

        List<Map<String, Object>> transactionData = transactions.stream().map(this::transactionMap).toList();
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", Instant.now());
        export.put("profile", profile);
        export.put("preferences", userPreferenceRepository.findById(job.getUser().getId()).map(this::preferencesMap).orElse(null));
        export.put("notificationPreferences", notificationPreferenceRepository.findById(job.getUser().getId())
            .map(this::notificationPreferencesMap).orElse(null));
        export.put("privacyPreferences", privacyPreferenceRepository.findById(job.getUser().getId())
            .map(this::privacyPreferencesMap).orElse(null));
        export.put("transactions", transactionData);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            write(zip, "data.json", objectMapper.writeValueAsBytes(export));
            write(zip, "transactions.csv", csv(transactionData).getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }

    private Map<String, Object> transactionMap(Transaction transaction) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", transaction.getId());
        value.put("date", transaction.getDate());
        value.put("amount", transaction.getAmount());
        value.put("category", transaction.getCategory());
        value.put("paymentMethod", transaction.getPaymentMethod());
        value.put("description", transaction.getDescription());
        value.put("inputType", transaction.getInputType());
        value.put("validationStatus", transaction.getValidationStatus());
        value.put("createdAt", transaction.getCreatedAt());
        value.put("updatedAt", transaction.getUpdatedAt());
        return value;
    }

    private Map<String, Object> preferencesMap(UserPreference value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currencyCode", value.getCurrencyCode());
        result.put("languageCode", value.getLanguageCode());
        result.put("regionCode", value.getRegionCode());
        result.put("timeZone", value.getTimeZone());
        result.put("themeMode", value.getThemeMode());
        result.put("revision", value.getRevision());
        return result;
    }

    private Map<String, Object> notificationPreferencesMap(NotificationPreference value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("spendingReminderEnabled", value.getSpendingReminders());
        result.put("reminderTime", value.getReminderTime());
        result.put("weeklySummaryEnabled", value.getWeeklySummary());
        result.put("monthlyReportEnabled", value.getMonthlyReport());
        result.put("securityAlertsEnabled", value.getSecurityAlerts());
        result.put("productUpdatesEnabled", value.getProductUpdates());
        result.put("revision", value.getRevision());
        return result;
    }

    private Map<String, Object> privacyPreferencesMap(PrivacyPreference value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("personalizedInsights", value.getPersonalizedInsights());
        result.put("anonymousAnalytics", value.getAnonymousAnalytics());
        result.put("policyVersion", value.getPolicyVersion());
        result.put("consentedAt", value.getConsentedAt());
        result.put("sourceDeviceId", value.getSourceDeviceId());
        result.put("sourcePlatform", value.getSourcePlatform());
        result.put("revision", value.getRevision());
        return result;
    }

    private String csv(List<Map<String, Object>> values) {
        StringBuilder csv = new StringBuilder("id,date,amount,category,paymentMethod,description,inputType,validationStatus\n");
        for (Map<String, Object> value : values) {
            csv.append(csvValue(value.get("id"))).append(',')
                .append(csvValue(value.get("date"))).append(',')
                .append(csvValue(value.get("amount"))).append(',')
                .append(csvValue(value.get("category"))).append(',')
                .append(csvValue(value.get("paymentMethod"))).append(',')
                .append(csvValue(value.get("description"))).append(',')
                .append(csvValue(value.get("inputType"))).append(',')
                .append(csvValue(value.get("validationStatus"))).append('\n');
        }
        return csv.toString();
    }

    private String csvValue(Object value) {
        if (value == null) return "";
        return '"' + value.toString().replace("\"", "\"\"") + '"';
    }

    private void write(ZipOutputStream zip, String name, byte[] content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }
}
