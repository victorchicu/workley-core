package ai.workley.core.chat.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface R2dbcMessageRepository extends ReactiveCrudRepository<MessageEntity, Long> {

    @Modifying
    @Query("UPDATE chat_messages SET reaction = :reaction WHERE message_id = :messageId")
    Mono<Integer> updateReaction(String messageId, String reaction);

    @Query("SELECT * FROM chat_messages WHERE message_id = :messageId")
    Mono<MessageEntity> findByMessageId(String messageId);

    @Query("SELECT * FROM chat_messages WHERE chat_id = :chatId LIMIT :limit")
    Flux<MessageEntity> findAllByChatId(String chatId, int limit);

    @Query("SELECT * FROM chat_messages WHERE chat_id = :chatId ORDER BY created_at ASC LIMIT :limit")
    Flux<MessageEntity> findAllByChatIdOrderByCreatedAtAsc(String chatId, int limit);
}
