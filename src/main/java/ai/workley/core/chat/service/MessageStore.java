package ai.workley.core.chat.service;

import ai.workley.core.chat.model.Message;
import ai.workley.core.chat.model.Content;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MessageStore {

    Mono<Integer> updateReaction(String messageId, String reaction);

    Mono<Message<? extends Content>> save(Message<? extends Content> message);

    Flux<Message<? extends Content>> loadAll(String chatId);

    Flux<Message<? extends Content>> loadRecent(String chatId, int limit);

    Mono<Message<? extends Content>> findByMessageId(String messageId);
}
