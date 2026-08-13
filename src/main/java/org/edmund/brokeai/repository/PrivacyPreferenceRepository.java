package org.edmund.brokeai.repository;

import org.edmund.brokeai.entity.PrivacyPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacyPreferenceRepository extends JpaRepository<PrivacyPreference, Long> {
}
