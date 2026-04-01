package ai.workley.core.chat.model;

public record TokenUsage(String model, int promptTokens, int completionTokens, int totalTokens) implements ReplyEvent {
    @Override
    public String type() {
        return "TOKEN_USAGE";
    }
}
