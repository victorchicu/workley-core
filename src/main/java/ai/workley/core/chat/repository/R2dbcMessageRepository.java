package ai.workley.core.chat.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface R2dbcMessageRepository extends ReactiveCrudRepository<MessageEntity, Long> {

    @Query("SELECT * FROM chat_messages WHERE chat_id = :chatId LIMIT :limit")
    Flux<MessageEntity> findAllByChatId(String chatId, int limit);

    @Query("SELECT * FROM chat_messages WHERE chat_id = :chatId ORDER BY created_at ASC LIMIT :limit")
    Flux<MessageEntity> findAllByChatIdOrderByCreatedAtAsc(String chatId, int limit);
}
