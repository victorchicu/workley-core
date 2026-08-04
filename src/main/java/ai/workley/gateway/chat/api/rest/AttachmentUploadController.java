package ai.workley.gateway.chat.api.rest;

import ai.workley.gateway.chat.application.exceptions.ApplicationError;
import ai.workley.gateway.chat.domain.payloads.AttachmentUploadPayload;
import ai.workley.gateway.chat.domain.payloads.ErrorPayload;
import ai.workley.gateway.chat.domain.payloads.Payload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@RequestMapping("/api/attachments")
@RestController
public class AttachmentUploadController {
    private static final Logger log = LoggerFactory.getLogger(AttachmentUploadController.class);
    private static final String STORAGE_DIRECTORY = "build/attachments";

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Payload>> upload(Principal actor, @RequestPart("file") FilePart file) {
        String actorName = actor != null ? actor.getName() : "anonymous";
        log.info("Uploading attachment (actor={}, filename={}, contentType={})", actorName, file.filename(), file.headers().getContentType());

        return DataBufferUtils.join(file.content())
                .map(this::toByteArray)
                .flatMap(bytes -> persistAttachment(file.filename(), bytes))
                .map(stored -> {
                    Payload payload = AttachmentUploadPayload.of(stored.originalFilename(), stored.size(), stored.storagePath());
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(payload);
                })
                .onErrorResume(ApplicationError.class, error -> {
                    log.error("Could not process attachment upload (actor={}, filename={})", actorName, file.filename(), error);
                    Payload payload = new ErrorPayload(error.getMessage());
                    ResponseEntity<Payload> response = ResponseEntity
                            .status(HttpStatus.BAD_REQUEST)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(payload);
                    return Mono.just(response);
                });
    }

    private byte[] toByteArray(DataBuffer dataBuffer) {
        try {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(dataBuffer);
        }
    }

    private Mono<StoredAttachment> persistAttachment(String filename, byte[] content) {
        return Mono.fromCallable(() -> doPersistAttachment(filename, content))
                .onErrorMap(IOException.class, error -> new ApplicationError("Oops! Could not store the uploaded file.", error))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private StoredAttachment doPersistAttachment(String filename, byte[] content) throws IOException {
        if (content.length == 0) {
            throw new ApplicationError("Oops! The uploaded file is empty.");
        }

        String sanitizedFilename = sanitizeFilename(filename);
        Path storageDirectory = Paths.get(STORAGE_DIRECTORY);
        Files.createDirectories(storageDirectory);

        String uniquePrefix = Instant.now().toString().replace(':', '-') + "-" + UUID.randomUUID();
        String storedFilename = uniquePrefix + "-" + sanitizedFilename;
        Path storedPath = storageDirectory.resolve(storedFilename);

        Files.write(storedPath, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

        return new StoredAttachment(sanitizedFilename, content.length, storedPath.toString());
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new ApplicationError("The uploaded file must have a name.");
        }

        String cleaned = filename.replace('\\', '/');
        int lastSlash = cleaned.lastIndexOf('/');
        if (lastSlash >= 0) {
            cleaned = cleaned.substring(lastSlash + 1);
        }

        if (cleaned.contains("..")) {
            throw new ApplicationError("The uploaded file name is invalid.");
        }

        String normalized = cleaned.strip();
        if (normalized.isEmpty()) {
            throw new ApplicationError("The uploaded file must have a name.");
        }

        return normalized;
    }

    private record StoredAttachment(String originalFilename, long size, String storagePath) {
    }
}
