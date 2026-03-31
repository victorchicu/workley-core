package ai.workley.core.chat.service;

import ai.workley.core.chat.model.ApplicationError;
import ai.workley.core.chat.repository.AttachmentEntity;
import ai.workley.core.chat.repository.R2dbcAttachmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import org.springframework.core.io.buffer.DataBufferUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AttachmentService {
    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final R2dbcAttachmentRepository attachmentRepository;
    private final S3StorageService s3StorageService;
    private final FileTextExtractor fileTextExtractor;
    private final long maxFileSize;
    private final List<String> allowedMimeTypes;
    private final int orphanTtlMinutes;

    public AttachmentService(
            R2dbcAttachmentRepository attachmentRepository,
            S3StorageService s3StorageService,
            FileTextExtractor fileTextExtractor,
            @Value("${attachment.max-file-size:10485760}") long maxFileSize,
            @Value("${attachment.allowed-mime-types}") List<String> allowedMimeTypes,
            @Value("${attachment.orphan-ttl-minutes:30}") int orphanTtlMinutes
    ) {
        this.attachmentRepository = attachmentRepository;
        this.s3StorageService = s3StorageService;
        this.fileTextExtractor = fileTextExtractor;
        this.maxFileSize = maxFileSize;
        this.allowedMimeTypes = allowedMimeTypes;
        this.orphanTtlMinutes = orphanTtlMinutes;
    }

    public Mono<AttachmentEntity> upload(UUID userId, FilePart filePart) {
        String filename = filePart.filename();
        String mimeType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString()
                : "application/octet-stream";

        if (!allowedMimeTypes.contains(mimeType)) {
            return Mono.error(new ApplicationError("File type not allowed: " + mimeType));
        }

        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .flatMap(bytes -> {
                    if (bytes.length > maxFileSize) {
                        return Mono.error(new ApplicationError("File too large. Maximum size is 10 MB."));
                    }

                    String extractedText = fileTextExtractor.extract(bytes, mimeType);

                    AttachmentEntity entity = new AttachmentEntity()
                            .setStorageKey("pending")
                            .setFilename(filename)
                            .setMimeType(mimeType)
                            .setFileSize(bytes.length)
                            .setExtractedText(extractedText)
                            .setUploadedBy(userId)
                            .setExpiresAt(Instant.now().plus(orphanTtlMinutes, ChronoUnit.MINUTES))
                            .setCreatedAt(Instant.now());

                    return attachmentRepository.save(entity)
                            .flatMap(saved -> {
                                String storageKey = "attachments/" + userId + "/" + saved.getId() + "/" + filename;
                                return s3StorageService.putObject(storageKey, bytes, mimeType)
                                        .then(attachmentRepository.save(saved.setStorageKey(storageKey)));
                            });
                });
    }

    public Mono<Integer> link(UUID attachmentId, UUID userId) {
        return attachmentRepository.linkAttachment(attachmentId, userId);
    }

    public Mono<Void> delete(UUID attachmentId, UUID userId) {
        return attachmentRepository.findUnlinkedByIdAndOwner(attachmentId, userId)
                .switchIfEmpty(Mono.error(new ApplicationError("Attachment not found or already linked.")))
                .flatMap(entity ->
                        s3StorageService.deleteObject(entity.getStorageKey())
                                .then(attachmentRepository.delete(entity))
                );
    }

    public Mono<AttachmentEntity> getById(UUID attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .switchIfEmpty(Mono.error(new ApplicationError("Attachment not found.")));
    }

    public Mono<byte[]> getFileBytes(UUID attachmentId) {
        return getById(attachmentId)
                .flatMap(entity -> s3StorageService.getObject(entity.getStorageKey()));
    }

    @Scheduled(fixedRate = 900000) // 15 minutes
    public void cleanupOrphans() {
        attachmentRepository.findOrphans()
                .flatMap(entity -> {
                    log.info("Cleaning up orphan attachment: id={}, filename={}", entity.getId(), entity.getFilename());
                    return s3StorageService.deleteObject(entity.getStorageKey())
                            .then(attachmentRepository.delete(entity))
                            .onErrorResume(error -> {
                                log.warn("Failed to cleanup orphan attachment: id={}", entity.getId(), error);
                                return Mono.empty();
                            });
                })
                .subscribe();
    }
}
