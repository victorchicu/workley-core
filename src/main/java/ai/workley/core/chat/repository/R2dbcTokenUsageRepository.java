package ai.workley.core.chat.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface R2dbcTokenUsageRepository extends ReactiveCrudRepository<TokenUsageEntity, Long> {

    Flux<TokenUsageEntity> findAllByChatId(String chatId);

    @Query("SELECT COALESCE(SUM(prompt_tokens), 0) AS prompt_tokens, " +
           "COALESCE(SUM(completion_tokens), 0) AS completion_tokens, " +
           "COALESCE(SUM(total_tokens), 0) AS total_tokens " +
           "FROM token_usage WHERE chat_id = :chatId")
    Flux<TokenUsageSummary> sumByChatId(String chatId);

    interface TokenUsageSummary {
        int getPromptTokens();
        int getCompletionTokens();
        int getTotalTokens();
    }
}