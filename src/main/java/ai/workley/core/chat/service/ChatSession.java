package ai.workley.core.chat.service;

import ai.workley.core.chat.model.Chat;
import ai.workley.core.chat.model.Message;
import ai.workley.core.chat.model.Content;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

@Service
public class ChatSession {
    private final ChatStore chatStore;
    private final MessageStore messageStore;

    public ChatSession(ChatStore chatStore, MessageStore messageStore) {
        this.chatStore = chatStore;
        this.messageStore = messageStore;
    }

    public Mono<Chat> saveChat(Chat chat) {
        return chatStore.saveChat(chat);
    }

    public Mono<Chat> findChat(String id, Collection<String> participants) {
        return chatStore.findChat(id, participants);
    }

    public Mono<Integer> updateReaction(String messageId, String reaction) {
        return messageStore.updateReaction(messageId, reaction);
    }

    public Mono<Message<? extends Content>> addMessage(Message<? extends Content> message) {
        return messageStore.save(message);
    }

    public Mono<Message<? extends Content>> findMessage(String messageId) {
        return messageStore.findByMessageId(messageId);
    }

    public Flux<Message<? extends Content>> loadAllHistory(String chatId) {
        return messageStore.loadAll(chatId);
    }

    public Flux<Message<? extends Content>> loadRecentHistory(String chatId, int limit) {
        return messageStore.loadRecent(chatId, limit);
    }
}
