package co.medellin.trancones.controller;

import co.medellin.trancones.dto.GroupRequest;
import co.medellin.trancones.dto.MessageRequest;
import co.medellin.trancones.model.ChatGroup;
import co.medellin.trancones.model.ChatMessage;
import co.medellin.trancones.repository.ChatGroupRepository;
import co.medellin.trancones.repository.ChatMessageRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Controlador de chat: grupos y mensajes entre usuarios.
 * Requiere autenticación JWT (ver SecurityConfig).
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@Validated
@RequiredArgsConstructor
public class ChatController {

    private final ChatGroupRepository groupRepository;
    private final ChatMessageRepository messageRepository;

    /**
     * Crea el grupo "General" la primera vez que se consulta (seed perezoso,
     * para no competir con el initializer de schema.sql al arrancar).
     */
    private Mono<ChatGroup> seedGeneralGroup() {
        return groupRepository.findByName("General")
                .switchIfEmpty(groupRepository.save(ChatGroup.builder()
                        .name("General")
                        .description("Grupo público para reportar y hablar del tráfico en Medellín y el área metropolitana")
                        .createdBy("sistema")
                        .createdAt(LocalDateTime.now())
                        .build()))
                .doOnSuccess(g -> log.info("[Chat] Grupo '{}' listo (id={})", g.getName(), g.getId()));
    }

    // ─── Grupos ──────────────────────────────────────────────────────────────

    /**
     * GET /api/chat/groups — lista los grupos existentes (General primero).
     */
    @GetMapping("/groups")
    public Flux<ChatGroup> listGroups() {
        return seedGeneralGroup().thenMany(groupRepository.findAllByOrderByCreatedAtAsc());
    }

    /**
     * POST /api/chat/groups — crea un grupo de chat.
     * El índice UNIQUE en chat_groups.name protege contra duplicados en caso
     * de carreras concurrentes; se captura la excepción para un mensaje limpio.
     */
    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ChatGroup> createGroup(@Valid @RequestBody GroupRequest request) {
        return currentUsername()
                .flatMap(username -> groupRepository.findByName(request.name())
                        .flatMap(existing -> Mono.error(new IllegalArgumentException(
                                "Ya existe un grupo llamado '" + existing.getName() + "'")))
                        .switchIfEmpty(Mono.defer(() -> {
                            ChatGroup group = ChatGroup.builder()
                                    .name(request.name())
                                    .description(request.description() == null ? "" : request.description())
                                    .createdBy(username)
                                    .createdAt(LocalDateTime.now())
                                    .build();
                            return groupRepository.save(group)
                                    .onErrorResume(org.springframework.dao.DuplicateKeyException.class,
                                            e -> Mono.error(new IllegalArgumentException(
                                                    "Ya existe un grupo llamado '" + request.name() + "'")))
                                    .doOnSuccess(g -> log.info("[Chat] {} creó el grupo '{}'", username, g.getName()));
                        }))
                        .cast(ChatGroup.class));
    }

    // ─── Mensajes ────────────────────────────────────────────────────────────

    /**
     * GET /api/chat/groups/{groupId}/messages — historial del grupo (ascendente).
     */
    @GetMapping("/groups/{groupId}/messages")
    public Flux<ChatMessage> listMessages(@PathVariable Long groupId) {
        return messageRepository.findByGroupIdOrderByCreatedAtAsc(groupId);
    }

    /**
     * POST /api/chat/groups/{groupId}/messages — envía un mensaje al grupo.
     */
    @PostMapping("/groups/{groupId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ChatMessage> sendMessage(@PathVariable Long groupId,
                                         @Valid @RequestBody MessageRequest request) {
        return groupRepository.findById(groupId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("El grupo no existe")))
                .flatMap(group -> currentUsername()
                        .flatMap(username -> {
                            ChatMessage message = ChatMessage.builder()
                                    .groupId(groupId)
                                    .senderUsername(username)
                                    .content(request.content())
                                    .createdAt(LocalDateTime.now())
                                    .build();
                            return messageRepository.save(message)
                                    .doOnSuccess(m -> log.info("[Chat] {} -> #{} ({} chars)",
                                            username, groupId, m.getContent().length()));
                        }));
    }

    private Mono<String> currentUsername() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ((Authentication) ctx.getAuthentication()).getName());
    }
}
