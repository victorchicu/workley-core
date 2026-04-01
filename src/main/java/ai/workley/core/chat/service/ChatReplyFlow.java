package ai.workley.core.chat.service;

import ai.workley.core.chat.model.AttachmentContent;
import ai.workley.core.chat.model.ReplyException;
import ai.workley.core.chat.model.Message;
import ai.workley.core.chat.model.Role;
import ai.workley.core.chat.model.Content;
import ai.workley.core.chat.model.ReplyChunk;
import ai.workley.core.chat.model.ReplyError;
import ai.workley.core.chat.model.ReplyCompletedContent;
import ai.workley.core.chat.model.TokenUsage;
import ai.workley.core.chat.repository.R2dbcAttachmentRepository;
import ai.workley.core.chat.repository.R2dbcTokenUsageRepository;
import ai.workley.core.chat.repository.TokenUsageEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ChatReplyFlow {
    private static final Logger log = LoggerFactory.getLogger(ChatReplyFlow.class);

    private final AiModel aiModel;
    private final ChatSession chatSession;
    private final ChunkDecoder chunkDecoder;
    private final PromptBuilder promptBuilder;
    private final ReplyAggregator replyAggregator;
    private final ChatChunkEmitter chatChunkEmitter;
    private final R2dbcAttachmentRepository attachmentRepository;
    private final R2dbcTokenUsageRepository tokenUsageRepository;

    public ChatReplyFlow(
            AiModel aiModel,
            ChatSession chatSession,
            ChunkDecoder chunkDecoder,
            PromptBuilder promptBuilder,
            ReplyAggregator replyAggregator,
            ChatChunkEmitter chatChunkEmitter,
            R2dbcAttachmentRepository attachmentRepository,
            R2dbcTokenUsageRepository tokenUsageRepository
    ) {
        this.aiModel = aiModel;
        this.chatSession = chatSession;
        this.chunkDecoder = chunkDecoder;
        this.promptBuilder = promptBuilder;
        this.replyAggregator = replyAggregator;
        this.chatChunkEmitter = chatChunkEmitter;
        this.attachmentRepository = attachmentRepository;
        this.tokenUsageRepository = tokenUsageRepository;
    }

    public Mono<Void> generate(String actor, String chatId, Message<? extends Content> message) {
        return chatSession.loadRecentHistory(chatId, 100)
                .collectList()
                .flatMap(history -> enrichWithAttachmentText(history)
                        .flatMap(enriched -> streamReply(actor, chatId, message, enriched)));
    }

    private Mono<List<Message<? extends Content>>> enrichWithAttachmentText(List<Message<? extends Content>> history) {
        List<Message<? extends Content>> enriched = new ArrayList<>(history);
        return Flux.fromIterable(history)
                .filter(m -> m.content() instanceof AttachmentContent)
                .flatMap(m -> {
                    AttachmentContent att = (AttachmentContent) m.content();
                    return attachmentRepository.findById(UUID.fromString(att.attachmentId()))
                            .map(entity -> Map.entry(m, entity));
                })
                .collectList()
                .map(entries -> {
                    for (var entry : entries) {
                        Message<? extends Content> original = entry.getKey();
                        String extractedText = entry.getValue().getExtractedText();
                        if (extractedText != null && !extractedText.isBlank()) {
                            int idx = enriched.indexOf(original);
                            if (idx >= 0) {
                                AttachmentContent att = (AttachmentContent) original.content();
                                String contextText = "User uploaded a file: " + att.filename() + "\n\nFile content:\n" + extractedText;
                                Message<ReplyChunk> contextMessage = Message.create(
                                        original.id(), original.chatId(), original.ownedBy(),
                                        original.role(), original.createdAt(), new ReplyChunk(contextText));
                                enriched.set(idx, contextMessage);
                            }
                        }
                    }
                    return enriched;
                });
    }

    private Mono<Void> streamReply(String actor, String chatId, Message<? extends Content> message,
                                    List<Message<? extends Content>> history) {
        final String replyId = UUID.randomUUID().toString();
        final AtomicReference<TokenUsage> usageRef = new AtomicReference<>();

        Prompt prompt = promptBuilder.build(message, history);

        Flux<ReplyChunk> chunks = aiModel.stream(prompt)
                .filter(event -> {
                    if (event instanceof TokenUsage usage) {
                        usageRef.set(usage);
                        return false;
                    }
                    return true;
                })
                .map(chunkDecoder::decode)
                .doOnNext(chunk ->
                        chatChunkEmitter.emit(
                                Message.create(replyId, chatId, actor, Role.ASSISTANT, Instant.now(), chunk))
                )
                .doOnComplete(() ->
                        chatChunkEmitter.emit(
                                Message.create(replyId, chatId, actor, Role.ASSISTANT, Instant.now(),
                                        new ReplyCompletedContent("\n")))
                )
                .onErrorResume(ReplyException.class, exception -> {
                    log.error("Error reply: {}", exception.getMessage());
                    chatChunkEmitter.emit(
                            Message.create(replyId, chatId, actor, Role.ASSISTANT, Instant.now(),
                                    new ReplyError(exception.getCode(), exception.getMessage())));
                    return Flux.empty();
                });

        return replyAggregator.aggregate(chunks)
                .flatMap(fullReply -> {
                    Message<ReplyChunk> replyMessage = Message.create(
                            replyId, chatId, actor, Role.ASSISTANT, Instant.now(), new ReplyChunk(fullReply));
                    return chatSession.addMessage(replyMessage);
                })
                .then(Mono.defer(() -> saveTokenUsage(replyId, chatId, usageRef.get())))
                .then();
    }

    private Mono<Void> saveTokenUsage(String messageId, String chatId, TokenUsage usage) {
        if (usage == null) {
            return Mono.empty();
        }
        TokenUsageEntity entity = new TokenUsageEntity()
                .setMessageId(messageId)
                .setChatId(chatId)
                .setModel(usage.model())
                .setPromptTokens(usage.promptTokens())
                .setCompletionTokens(usage.completionTokens())
                .setTotalTokens(usage.totalTokens());
        return tokenUsageRepository.save(entity)
                .doOnError(error -> log.error("Failed to save token usage (messageId={}, chatId={})", messageId, chatId, error))
                .onErrorResume(error -> Mono.empty())
                .then();
    }
}
