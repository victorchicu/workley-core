package ai.workley.core.chat.service;

import ai.workley.core.chat.model.ErrorReply;
import ai.workley.core.chat.model.ErrorCode;
import ai.workley.core.chat.model.ReplyEvent;
import ai.workley.core.chat.model.ChunkReply;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Primary
@Component
public class GptAiModel implements AiModel {

    private final OpenAiChatModel openAiChatModel;
    private final ReactiveCircuitBreaker reactiveCircuitBreaker;

    public GptAiModel(OpenAiChatModel openAiChatModel, ReactiveResilience4JCircuitBreakerFactory reactiveResilience4JCircuitBreakerFactory) {
        this.openAiChatModel = openAiChatModel;
        this.reactiveCircuitBreaker = reactiveResilience4JCircuitBreakerFactory.create("gpt-ai-model");
    }

    @Override
    public Mono<ReplyEvent> call(Prompt prompt) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Flux<ReplyEvent> stream(Prompt prompt) {
        return reactiveCircuitBreaker.run(internalStream(prompt), this::fallback);
    }

    private ErrorReply toError(Throwable t) {
        if (t instanceof CallNotPermittedException) {
            return new ErrorReply(ErrorCode.AI_MODEL_CIRCUIT_OPEN, "Circuit breaker open for GPT model");
        }

        if (t instanceof WebClientRequestException) {
            return new ErrorReply(ErrorCode.AI_MODEL_BACKEND_UNREACHABLE, "GPT backend unreachable");
        }

        if (t instanceof WebClientResponseException wcre) {
            return new ErrorReply(ErrorCode.AI_MODEL_HTTP_ERROR, String.format( "GPT returned HTTP error: %s",  wcre.getStatusCode()));
        }

        return new ErrorReply(
                ErrorCode.UNKNOWN,
                "An unexpected error occurred in GPT. Please try again."
        );
    }

    private Flux<ReplyEvent> fallback(Throwable t) {
        return Flux.just(toError(t));
    }

    private Flux<ReplyEvent> internalStream(Prompt prompt) {
        return openAiChatModel.stream(prompt)
                .timeout(Duration.ofSeconds(120))
                .flatMapIterable(resp -> {
                    List<Generation> gens = resp != null
                            ? resp.getResults()
                            : null;
                    return gens != null ? gens : List.of();
                })
                .map(Generation::getOutput)
                .mapNotNull(AbstractMessage::getText)
                .map(ChunkReply::new);
    }
}
