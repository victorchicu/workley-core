package ai.workley.core.chat.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChunkReply.class, name = "TEXT_CHUNK"),
        @JsonSubTypes.Type(value = ErrorReply.class, name = "ERROR_REPLY"),
        @JsonSubTypes.Type(value = TokenUsage.class, name = "TOKEN_USAGE")
})
public interface ReplyEvent {

    String type();
}
