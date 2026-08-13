package org.edmund.brokeai.repository;

import org.edmund.brokeai.entity.FaqArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FaqArticleRepository extends JpaRepository<FaqArticle, UUID> {
    Page<FaqArticle> findByLocaleAndPublicationStatusOrderByDisplayOrder(
        String locale,
        String publicationStatus,
        Pageable pageable
    );

    Page<FaqArticle> findByLocaleAndCategoryAndPublicationStatusOrderByDisplayOrder(
        String locale,
        String category,
        String publicationStatus,
        Pageable pageable
    );
}
