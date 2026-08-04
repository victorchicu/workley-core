package ai.workley.gateway.chat.domain.payloads;

public record AttachmentUploadPayload(String filename, long size, String path) implements Payload {

    public static AttachmentUploadPayload of(String filename, long size, String path) {
        return new AttachmentUploadPayload(filename, size, path);
    }
}
