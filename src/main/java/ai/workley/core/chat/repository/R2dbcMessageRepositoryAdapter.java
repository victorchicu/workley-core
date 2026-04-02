package ai.workley.core.chat.repository;

import ai.workley.core.chat.service.MessageStore;
import ai.workley.core.chat.model.Message;
import ai.workley.core.chat.model.Role;
import ai.workley.core.chat.model.Content;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class R2dbcMessageRepositoryAdapter implements MessageStore {
    private final R2dbcMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public R2dbcMessageRepositoryAdapter(R2dbcMessageRepository messageRepository, ObjectMapper objectMapper) {
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Integer> updateReaction(String messageId, String reaction) {
        return messageRepository.updateReaction(messageId, reaction);
    }

    @Override
    public Mono<Message<? extends Content>> save(Message<? extends Content> message) {
        MessageEntity entity = toMessageEntity(message);
        return messageRepository.save(entity)
                .map(this::toMessage);
    }

    @Override
    public Flux<Message<? extends Content>> loadAll(String chatId) {
        return messageRepository.findAllByChatId(chatId, 100)
                .map(this::toMessage);
    }

    @Override
    public Flux<Message<? extends Content>> loadRecent(String chatId, int limit) {
        return messageRepository.findAllByChatIdOrderByCreatedAtAsc(chatId, limit)
                .map(this::toMessage);
    }

    @Override
    public Mono<Message<? extends Content>> findByMessageId(String messageId) {
        return messageRepository.findByMessageId(messageId)
                .map(this::toMessage);
    }


    private Message<? extends Content> toMessage(MessageEntity source) {
        try {
            Content content = objectMapper.readValue(source.getContent().asString(), Content.class);
            return Message.create(
                    source.getMessageId(),
                    source.getChatId(),
                    source.getOwnedBy(),
                    Role.valueOf(source.getRole()),
                    source.getCreatedAt(),
                    content,
                    source.getReaction()
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize message content", e);
        }
    }

    private MessageEntity toMessageEntity(Message<? extends Content> source) {
        try {
            String contentJson = objectMapper.writeValueAsString(source.content());
            return new MessageEntity()
                    .setMessageId(source.id())
                    .setChatId(source.chatId())
                    .setOwnedBy(source.ownedBy())
                    .setRole(source.role().name())
                    .setCreatedAt(source.createdAt())
                    .setContent(Json.of(contentJson))
                    .setReaction(source.reaction());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize message content", e);
        }
    }
}
