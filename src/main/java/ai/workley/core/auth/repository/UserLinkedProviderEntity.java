package ai.workley.core.auth.repository;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("user_linked_providers")
public class UserLinkedProviderEntity {
    @Id
    private Long id;
    @Column("user_id")
    private UUID userId;
    private String provider;
    private String subject;
    private String email;
    @Column("created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public UserLinkedProviderEntity setId(Long id) {
        this.id = id;
        return this;
    }

    public UUID getUserId() {
        return userId;
    }

    public UserLinkedProviderEntity setUserId(UUID userId) {
        this.userId = userId;
        return this;
    }

    public String getProvider() {
        return provider;
    }

    public UserLinkedProviderEntity setProvider(String provider) {
        this.provider = provider;
        return this;
    }

    public String getSubject() {
        return subject;
    }

    public UserLinkedProviderEntity setSubject(String subject) {
        this.subject = subject;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public UserLinkedProviderEntity setEmail(String email) {
        this.email = email;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UserLinkedProviderEntity setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }
}
