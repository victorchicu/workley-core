package ai.workley.core.chat.repository;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("attachments")
public class AttachmentEntity {
    @Id
    private UUID id;
    @Column("storage_key")
    private String storageKey;
    private String filename;
    @Column("mime_type")
    private String mimeType;
    @Column("file_size")
    private long fileSize;
    @Column("extracted_text")
    private String extractedText;
    @Column("uploaded_by")
    private UUID uploadedBy;
    @Column("expires_at")
    private Instant expiresAt;
    @Column("created_at")
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public AttachmentEntity setId(UUID id) {
        this.id = id;
        return this;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public AttachmentEntity setStorageKey(String storageKey) {
        this.storageKey = storageKey;
        return this;
    }

    public String getFilename() {
        return filename;
    }

    public AttachmentEntity setFilename(String filename) {
        this.filename = filename;
        return this;
    }

    public String getMimeType() {
        return mimeType;
    }

    public AttachmentEntity setMimeType(String mimeType) {
        this.mimeType = mimeType;
        return this;
    }

    public long getFileSize() {
        return fileSize;
    }

    public AttachmentEntity setFileSize(long fileSize) {
        this.fileSize = fileSize;
        return this;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public AttachmentEntity setExtractedText(String extractedText) {
        this.extractedText = extractedText;
        return this;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public AttachmentEntity setUploadedBy(UUID uploadedBy) {
        this.uploadedBy = uploadedBy;
        return this;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public AttachmentEntity setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public AttachmentEntity setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }
}
