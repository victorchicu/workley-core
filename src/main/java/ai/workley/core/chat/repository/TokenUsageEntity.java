package ai.workley.core.chat.repository;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("token_usage")
public class TokenUsageEntity {
    @Id
    private Long id;
    @Column("message_id")
    private String messageId;
    @Column("chat_id")
    private String chatId;
    private String model;
    @Column("prompt_tokens")
    private int promptTokens;
    @Column("completion_tokens")
    private int completionTokens;
    @Column("total_tokens")
    private int totalTokens;
    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public TokenUsageEntity setId(Long id) {
        this.id = id;
        return this;
    }

    public String getMessageId() {
        return messageId;
    }

    public TokenUsageEntity setMessageId(String messageId) {
        this.messageId = messageId;
        return this;
    }

    public String getChatId() {
        return chatId;
    }

    public TokenUsageEntity setChatId(String chatId) {
        this.chatId = chatId;
        return this;
    }

    public String getModel() {
        return model;
    }

    public TokenUsageEntity setModel(String model) {
        this.model = model;
        return this;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public TokenUsageEntity setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
        return this;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public TokenUsageEntity setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
        return this;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public TokenUsageEntity setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public TokenUsageEntity setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }
}