package ai.workley.core.chat.controller;

import ai.workley.core.chat.model.ApplicationError;
import ai.workley.core.chat.model.ErrorPayload;
import ai.workley.core.chat.repository.AttachmentEntity;
import ai.workley.core.chat.service.AttachmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {
    private static final Logger log = LoggerFactory.getLogger(AttachmentController.class);

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<?>> upload(Principal principal, @RequestPart("file") FilePart file) {
        log.info("Upload attachment (principal={}, filename={})", principal.getName(), file.filename());
        UUID userId = UUID.fromString(principal.getName());
        return attachmentService.upload(userId, file)
                .<ResponseEntity<?>>map(entity -> ResponseEntity.ok().body(Map.of(
                        "attachmentId", entity.getId().toString(),
                        "filename", entity.getFilename(),
                        "mimeType", entity.getMimeType(),
                        "fileSize", entity.getFileSize()
                )))
                .onErrorResume(ApplicationError.class, error ->
                        Mono.just(ResponseEntity.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(new ErrorPayload(error.getMessage()))));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<?>> getMetadata(Principal principal, @PathVariable UUID id) {
        return attachmentService.getById(id)
                .<ResponseEntity<?>>map(entity -> ResponseEntity.ok().body(Map.of(
                        "attachmentId", entity.getId().toString(),
                        "filename", entity.getFilename(),
                        "mimeType", entity.getMimeType(),
                        "fileSize", entity.getFileSize()
                )))
                .onErrorResume(ApplicationError.class, error ->
                        Mono.just(ResponseEntity.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(new ErrorPayload(error.getMessage()))));
    }

    @GetMapping("/{id}/download")
    public Mono<ResponseEntity<byte[]>> download(Principal principal, @PathVariable UUID id) {
        return attachmentService.getById(id)
                .flatMap(entity ->
                        attachmentService.getFileBytes(id)
                                .map(bytes -> {
                                    String disposition = "application/pdf".equals(entity.getMimeType())
                                            ? "inline; filename=\"" + entity.getFilename() + "\""
                                            : "attachment; filename=\"" + entity.getFilename() + "\"";
                                    return ResponseEntity.ok()
                                            .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                                            .contentType(MediaType.parseMediaType(entity.getMimeType()))
                                            .contentLength(entity.getFileSize())
                                            .body(bytes);
                                })
                );
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<?>> delete(Principal principal, @PathVariable UUID id) {
        log.info("Delete attachment (principal={}, attachmentId={})", principal.getName(), id);
        UUID userId = UUID.fromString(principal.getName());
        return attachmentService.delete(id, userId)
                .<ResponseEntity<?>>thenReturn(ResponseEntity.noContent().<Void>build())
                .onErrorResume(ApplicationError.class, error ->
                        Mono.just(ResponseEntity.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(new ErrorPayload(error.getMessage()))));
    }
}
