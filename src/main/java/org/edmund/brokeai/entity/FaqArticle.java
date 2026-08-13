package org.edmund.brokeai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "faq_articles")
@Data
public class FaqArticle {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 35)
    private String locale;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private String body;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "publication_status", nullable = false, length = 20)
    private String publicationStatus = "draft";

    @Column(name = "minimum_app_version", length = 30)
    private String minimumAppVersion;

    @Column(name = "maximum_app_version", length = 30)
    private String maximumAppVersion;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private Long revision = 1L;
}
