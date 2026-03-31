package ai.workley.core.chat.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({

        @JsonSubTypes.Type(value = Text.class, name = "TEXT"),
        @JsonSubTypes.Type(value = ReplyChunk.class, name = "REPLY_CHUNK"),
        @JsonSubTypes.Type(value = ReplyCompletedContent.class, name = "REPLY_COMPLETED"),
        @JsonSubTypes.Type(value = ReplyError.class, name = "REPLY_ERROR"),
        @JsonSubTypes.Type(value = AttachmentContent.class, name = "ATTACHMENT")
})
public interface Content {

    String type();
}
