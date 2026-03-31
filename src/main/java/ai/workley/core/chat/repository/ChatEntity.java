package ai.workley.core.chat.repository;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import io.r2dbc.postgresql.codec.Json;

import java.time.Instant;

@Table("chats")
public class ChatEntity {
    @Id
    private Long id;
    @Column("chat_id")
    private String chatId;
    private Json summary;
    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public ChatEntity setId(Long id) {
        this.id = id;
        return this;
    }

    public String getChatId() {
        return chatId;
    }

    public ChatEntity setChatId(String chatId) {
        this.chatId = chatId;
        return this;
    }

    public Json getSummary() {
        return summary;
    }

    public ChatEntity setSummary(Json summary) {
        this.summary = summary;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public ChatEntity setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }
}
