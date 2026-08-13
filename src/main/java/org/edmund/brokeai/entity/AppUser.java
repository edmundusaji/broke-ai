package org.edmund.brokeai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "users")
@Data
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 30, columnDefinition = "citext")
    private String username;

    @Column(unique = true, columnDefinition = "citext")
    private String email;

    @JsonIgnore
    @Column(name = "password_hash")
    private String password;

    @Column(name = "is_guest", nullable = false, columnDefinition = "boolean default false")
    private Boolean isGuest = false;

    @Column(name = "ai_trial_count", nullable = false, columnDefinition = "integer default 2")
    private Integer aiTrialCount = 2;

    @Column(name = "pending_email", columnDefinition = "citext")
    private String pendingEmail;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "phone_e164", length = 20)
    private String phone;

    @Column(name = "avatar_object_key")
    private String avatarObjectKey;

    @Column(nullable = false, length = 30)
    private String status = "active";

    @Column(name = "profile_revision", nullable = false)
    @Version
    private Long profileRevision = 1L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
