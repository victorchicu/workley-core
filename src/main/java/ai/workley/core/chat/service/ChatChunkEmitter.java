package ai.workley.core.chat.service;

import ai.workley.core.chat.model.Message;
import ai.workley.core.chat.model.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

@Component
public class ChatChunkEmitter {
    private static final Logger log = LoggerFactory.getLogger(ChatChunkEmitter.class);

    private final Sinks.Many<Message<? extends Content>> chatSessionSink;

    public ChatChunkEmitter(Sinks.Many<Message<? extends Content>> chatSessionSink) {
        this.chatSessionSink = chatSessionSink;
    }

    public void emit(Message<?> message) {
        chatSessionSink.emitNext(message, (signalType, emitResult) -> {
            if (emitResult == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
                return true; // retry on concurrent emission
            }
            log.warn("ReplyChunk dropped (actor={}, chatId={}, reason={})", message.ownedBy(), message.chatId(), emitResult);
            return false;
        });
    }
}
