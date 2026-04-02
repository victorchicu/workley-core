package ai.workley.core.chat.repository;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import io.r2dbc.postgresql.codec.Json;

import java.time.Instant;

@Table("chat_messages")
public class MessageEntity {
    @Id
    private Long id;
    @Column("message_id")
    private String messageId;
    @Column("chat_id")
    private String chatId;
    private String role;
    @Column("owned_by")
    private String ownedBy;
    private Json content;
    private String reaction;
    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public MessageEntity setId(Long id) {
        this.id = id;
        return this;
    }

    public String getMessageId() {
        return messageId;
    }

    public MessageEntity setMessageId(String messageId) {
        this.messageId = messageId;
        return this;
    }

    public String getChatId() {
        return chatId;
    }

    public MessageEntity setChatId(String chatId) {
        this.chatId = chatId;
        return this;
    }

    public String getRole() {
        return role;
    }

    public MessageEntity setRole(String role) {
        this.role = role;
        return this;
    }

    public String getOwnedBy() {
        return ownedBy;
    }

    public MessageEntity setOwnedBy(String ownedBy) {
        this.ownedBy = ownedBy;
        return this;
    }

    public Json getContent() {
        return content;
    }

    public MessageEntity setContent(Json content) {
        this.content = content;
        return this;
    }

    public String getReaction() {
        return reaction;
    }

    public MessageEntity setReaction(String reaction) {
        this.reaction = reaction;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public MessageEntity setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }
}
