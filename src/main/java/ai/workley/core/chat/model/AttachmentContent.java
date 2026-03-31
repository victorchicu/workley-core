package ai.workley.core.chat.model;

public record AttachmentContent(
        String attachmentId,
        String filename,
        String mimeType,
        long fileSize
) implements Content {

    @Override
    public String type() {
        return "ATTACHMENT";
    }
}
