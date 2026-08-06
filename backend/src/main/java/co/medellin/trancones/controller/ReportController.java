package co.medellin.trancones.controller;

import co.medellin.trancones.dto.ReportRequest;
import co.medellin.trancones.model.UserReport;
import co.medellin.trancones.repository.UserReportRepository;
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
 * Controlador de la capa de usuario: informes colocados en el mapa.
 * Todos los endpoints requieren autenticación JWT (ver SecurityConfig).
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@Validated
@RequiredArgsConstructor
public class ReportController {

    private final UserReportRepository reportRepository;

    private static final int MAX_REPORTS_PER_USER = 50;

    /**
     * GET /api/reports — todos los informes activos (para pintarlos en el mapa).
     */
    @GetMapping
    public Flux<UserReport> listReports() {
        return reportRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * POST /api/reports — coloca un informe en el mapa.
     * Limita a 50 informes por usuario para evitar abuso.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserReport> createReport(@Valid @RequestBody ReportRequest request) {
        return currentUsername()
                .flatMap(username -> reportRepository.countByUsername(username)
                        .flatMap(count -> {
                            if (count >= MAX_REPORTS_PER_USER) {
                                return Mono.error(new IllegalArgumentException(
                                        "Has alcanzado el límite de " + MAX_REPORTS_PER_USER
                                                + " informes activos. Borra algunos antes de añadir más."));
                            }
                            UserReport report = UserReport.builder()
                                    .username(username)
                                    .type(request.type())
                                    .description(request.description() == null ? "" : request.description())
                                    .lat(request.lat())
                                    .lng(request.lng())
                                    .createdAt(LocalDateTime.now())
                                    .build();
                            return reportRepository.save(report)
                                    .doOnSuccess(r -> log.info("[Report] {} creó informe {} en ({}, {})",
                                            username, r.getType(), r.getLat(), r.getLng()));
                        }));
    }

    /**
     * DELETE /api/reports/{id} — borra un informe propio (o cualquiera si es admin).
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteReport(@PathVariable Long id) {
        return currentUsername()
                .flatMap(username -> reportRepository.findById(id)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Informe no encontrado")))
                        .flatMap(report -> isAdmin().flatMap(admin -> {
                            if (admin || username.equals(report.getUsername())) {
                                return reportRepository.deleteById(id)
                                        .thenReturn(ResponseEntity.noContent().<Void>build());
                            }
                            return Mono.error(new IllegalArgumentException(
                                    "Solo puedes borrar tus propios informes"));
                        })));
    }

    private Mono<String> currentUsername() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ((Authentication) ctx.getAuthentication()).getName());
    }

    private Mono<Boolean> isAdmin() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ((Authentication) ctx.getAuthentication()).getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }
}
