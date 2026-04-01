package ai.workley.core.chat.service;

import ai.workley.core.chat.model.ErrorReply;
import ai.workley.core.chat.model.ErrorCode;
import ai.workley.core.chat.model.ReplyEvent;
import ai.workley.core.chat.model.ChunkReply;
import ai.workley.core.chat.model.TokenUsage;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
public class OllamaAiModel implements AiModel {
    private static final Logger log = LoggerFactory.getLogger(OllamaAiModel.class);

    private final OllamaChatModel ollamaChatModel;
    private final ReactiveCircuitBreaker reactiveCircuitBreaker;

    public OllamaAiModel(OllamaChatModel ollamaChatModel, ReactiveResilience4JCircuitBreakerFactory reactiveResilience4JCircuitBreakerFactory, MessageStore messageStore) {
        this.ollamaChatModel = ollamaChatModel;
        this.reactiveCircuitBreaker = reactiveResilience4JCircuitBreakerFactory.create("ollama-ai-model");
    }

    @Override
    public Mono<ReplyEvent> call(Prompt prompt) {
        return reactiveCircuitBreaker.run(internalCall(prompt), this::callFallback);
    }

    @Override
    public Flux<ReplyEvent> stream(Prompt prompt) {
        return reactiveCircuitBreaker.run(internalStream(prompt), this::streamFallback);
    }


    private ErrorReply toError(Throwable t) {
        if (t instanceof CallNotPermittedException) {
            return new ErrorReply(ErrorCode.AI_MODEL_CIRCUIT_OPEN, "Circuit breaker open for Ollama model");
        }

        if (t instanceof WebClientRequestException) {
            return new ErrorReply(ErrorCode.AI_MODEL_BACKEND_UNREACHABLE, "Ollama backend unreachable");
        }

        if (t instanceof WebClientResponseException wcre) {
            return new ErrorReply(ErrorCode.AI_MODEL_HTTP_ERROR, String.format("Ollama returned HTTP error: %s", wcre.getStatusCode()));
        }

        return new ErrorReply(
                ErrorCode.UNKNOWN,
                "An unexpected error occurred in Ollama. Please try again."
        );
    }

    private Mono<ReplyEvent> callFallback(Throwable t) {
        return Mono.just(toError(t));
    }

    private Flux<ReplyEvent> streamFallback(Throwable t) {
        return Flux.just(toError(t));
    }

    private Mono<ReplyEvent> internalCall(Prompt prompt) {
        ChatResponse chatResponse = ollamaChatModel.call(prompt);

        List<Generation> generations =
                chatResponse != null
                        ? chatResponse.getResults()
                        : null;

        String fullReply =
                generations != null
                        ?
                        generations.stream()
                                .map(Generation::getOutput)
                                .map(AbstractMessage::getText)
                                .collect(Collectors.joining("\n"))
                        : "";

        return Mono.just(new ChunkReply(fullReply));
    }

    private Flux<ReplyEvent> internalStream(Prompt prompt) {
        AtomicReference<Usage> lastUsage = new AtomicReference<>();
        String model = prompt.getOptions() != null && prompt.getOptions().getModel() != null
                ? prompt.getOptions().getModel()
                : ollamaChatModel.getDefaultOptions().getModel();

        Flux<ReplyEvent> textChunks = ollamaChatModel.stream(prompt)
                .timeout(Duration.ofSeconds(30))
                .doOnNext(chatResponse -> {
                    if (chatResponse != null && chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
                        Usage usage = chatResponse.getMetadata().getUsage();
                        if (usage.getTotalTokens() > 0) {
                            lastUsage.set(usage);
                        }
                    }
                })
                .flatMapIterable(chatResponse -> {
                    List<Generation> generations =
                            chatResponse != null
                                    ? chatResponse.getResults()
                                    : null;
                    return generations != null
                            ? generations
                            : List.of();
                })
                .map(Generation::getOutput)
                .mapNotNull(AbstractMessage::getText)
                .map(ChunkReply::new);

        if (model == null) {
            log.warn("Model name not available in prompt options, token usage will not be tracked");
            return textChunks;
        }

        return Flux.concat(textChunks, Mono.fromSupplier(() -> {
            Usage usage = lastUsage.get();
            if (usage != null) {
                return new TokenUsage(model,
                        intValue(usage.getPromptTokens()),
                        intValue(usage.getCompletionTokens()),
                        intValue(usage.getTotalTokens()));
            }
            return new TokenUsage(model, 0, 0, 0);
        }));
    }

    private static int intValue(Integer value) {
        return value != null ? value : 0;
    }
}
