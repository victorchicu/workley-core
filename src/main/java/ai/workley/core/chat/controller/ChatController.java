package ai.workley.core.chat.controller;

import ai.workley.core.chat.model.ApplicationError;
import ai.workley.core.chat.model.ErrorPayload;
import ai.workley.core.chat.model.Payload;
import ai.workley.core.chat.model.Role;
import ai.workley.core.chat.service.ChatService;
import ai.workley.core.idempotency.IdempotencyKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.security.Principal;

@RestController
@RequestMapping("/api/chats")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    public record CreateChatRequest(String prompt, String attachmentId) {
    }

    public record AddMessageRequest(String text, String attachmentId) {
    }

    public record ReactToMessageRequest(String reaction) {
    }

    @GetMapping("/{chatId}")
    public Mono<ResponseEntity<Payload>> getChat(Principal principal, @PathVariable String chatId) {
        log.info("Get chat (principal={}, chatId={})", principal.getName(), chatId);
        return chatService.getChat(principal.getName(), chatId)
                .map(payload -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body((Payload) payload))
                .onErrorResume(ApplicationError.class, error ->
                        Mono.just(ResponseEntity.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(new ErrorPayload(error.getMessage()))));
    }

    @PostMapping
    @IdempotencyKey
    public Mono<ResponseEntity<Payload>> createChat(Principal principal, @RequestBody CreateChatRequest request) {
        return Mono.deferContextual(contextView -> {
            log.info("Create chat (principal={})", principal.getName());
            return chatService.createChat(principal.getName(), resolveRole(principal), request.prompt(), request.attachmentId())
                    .map(payload -> ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body((Payload) payload))
                    .onErrorResume(ApplicationError.class, error ->
                            Mono.just(ResponseEntity.badRequest()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(new ErrorPayload(error.getMessage()))));
        });
    }

    @PatchMapping("/{chatId}/messages/{messageId}/reaction")
    public Mono<ResponseEntity<Object>> addReaction(Principal principal, @PathVariable String chatId, @PathVariable String messageId, @RequestBody ReactToMessageRequest request) {
        log.info("React to message (principal={}, chatId={}, messageId={}, reaction={})",
                principal.getName(), chatId, messageId, request.reaction());
        return chatService.addReaction(principal.getName(), chatId, messageId, request.reaction())
                .map(newReaction ->
                        ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body((Object) java.util.Map.of("reaction", newReaction))
                )
                .onErrorResume(ApplicationError.class, error ->
                        Mono.just(ResponseEntity.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(new ErrorPayload(error.getMessage()))));
    }

    @PostMapping("/{chatId}/messages")
    @IdempotencyKey
    public Mono<ResponseEntity<Payload>> addMessage(Principal principal, @PathVariable String chatId, @RequestBody AddMessageRequest request) {
        return Mono.deferContextual(contextView -> {
            log.info("Add message (principal={}, chatId={})", principal.getName(), chatId);
            return chatService.addMessage(principal.getName(), resolveRole(principal), chatId, request.text(), request.attachmentId())
                    .map(payload -> ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body((Payload) payload))
                    .onErrorResume(ApplicationError.class, error ->
                            Mono.just(ResponseEntity.badRequest()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(new ErrorPayload(error.getMessage()))));
        });
    }

    private Role resolveRole(Principal principal) {
        if (principal instanceof Authentication authentication) {
            return authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority).findFirst().map(Role::of)
                    .orElse(Role.UNKNOWN);
        }
        return Role.UNKNOWN;
    }
}
