package ai.workley.core.chat.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface R2dbcChatRepository extends ReactiveCrudRepository<ChatEntity, Long> {

    @Query("""
            SELECT cs.* FROM chats cs
            JOIN chat_participants cp ON cs.chat_id = cp.chat_id
            WHERE cs.chat_id = :chatId AND cp.participant_id = ANY(:participantIds)
            GROUP BY cs.id
            HAVING COUNT(DISTINCT cp.participant_id) = :participantCount
            """)
    Mono<ChatEntity> findChat(String chatId, String[] participantIds, int participantCount);
}
