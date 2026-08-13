package org.edmund.brokeai.service.serviceimpl;

import org.edmund.brokeai.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Currency;
import java.util.Locale;

final class ServiceSupport {

    private ServiceSupport() {
    }

    static long parseRevision(String ifMatch) {
        if (ifMatch == null || !ifMatch.matches("\"[0-9]+\"")) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "If-Match must contain a quoted numeric revision."
            );
        }
        return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
    }

    static void requireRevision(long actual, String ifMatch) {
        if (parseRevision(ifMatch) != actual) {
            throw new ApiException(
                HttpStatus.PRECONDITION_FAILED,
                "CONFLICTING_UPDATE",
                "This resource was updated on another device."
            );
        }
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static void validateCurrency(String code) {
        try {
            Currency.getInstance(code);
        } catch (IllegalArgumentException exception) {
            throw validation("currencyCode", "Unsupported ISO 4217 currency code.");
        }
    }

    static void validateLanguage(String code) {
        if (code == null || code.isBlank() || "und".equals(Locale.forLanguageTag(code).getLanguage())) {
            throw validation("languageCode", "Unsupported BCP 47 language code.");
        }
    }

    static void validateRegion(String code) {
        if (code == null || !SetHolder.ISO_COUNTRIES.contains(code)) {
            throw validation("regionCode", "Unsupported ISO 3166-1 region code.");
        }
    }

    static void validateTimeZone(String timeZone) {
        try {
            ZoneId.of(timeZone);
        } catch (DateTimeException exception) {
            throw validation("timeZone", "Unsupported IANA time-zone identifier.");
        }
    }

    static ApiException validation(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, field);
    }

    private static final class SetHolder {
        private static final java.util.Set<String> ISO_COUNTRIES = java.util.Set.of(Locale.getISOCountries());
    }
}
