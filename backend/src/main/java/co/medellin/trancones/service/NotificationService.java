package co.medellin.trancones.service;

import co.medellin.trancones.dto.PushSubscriptionRequest;
import co.medellin.trancones.dto.SegmentStatus;
import co.medellin.trancones.model.PushSubscription;
import co.medellin.trancones.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;

/**
 * Gestiona suscripciones Web Push VAPID y envía notificaciones de congestión severa.
 * Requisitos: 8.2, 8.3, 8.5, 8.6, 8.8, 11.3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final PushSubscriptionRepository subscriptionRepository;

    @Value("${vapid.public-key}")
    private String vapidPublicKey;

    @Value("${vapid.private-key}")
    private String vapidPrivateKey;

    @Value("${vapid.subject}")
    private String vapidSubject;

    /**
     * Guarda una nueva suscripción VAPID.
     * Requisito 8.2
     */
    public Mono<Void> subscribe(PushSubscriptionRequest request) {
        PushSubscription entity = PushSubscription.builder()
                .endpoint(request.endpoint())
                .p256dh(request.p256dh())
                .auth(request.auth())
                .createdAt(LocalDateTime.now())
                .build();
        return subscriptionRepository.save(entity)
                .doOnSuccess(s -> log.info("[Push] Suscripción registrada: {}", request.endpoint()))
                .then();
    }

    /**
     * Elimina una suscripción VAPID por endpoint.
     * Requisito 8.3
     */
    public Mono<Void> unsubscribe(String endpoint) {
        return subscriptionRepository.deleteByEndpoint(endpoint)
                .doOnSuccess(v -> log.info("[Push] Suscripción eliminada: {}", endpoint));
    }

    /**
     * Envía notificación Web Push a todos los suscriptores si speedRatio < 0.30.
     * Requisito 8.5, 8.6, 11.3
     */
    public Mono<Void> notifyBottleneck(SegmentStatus bottleneck) {
        if (bottleneck == null || bottleneck.speedRatio() >= 0.30) {
            return Mono.empty();
        }

        String body = String.format(
                "¡Trancón severo detectado en %s! speedRatio: %.2f",
                bottleneck.segmentName(), bottleneck.speedRatio());

        return subscriptionRepository.findAll()
                .flatMap(sub -> sendPushNotification(sub, body)
                        .onErrorResume(e -> {
                            log.warn("[Push] Error enviando a {}: {}", sub.getEndpoint(), e.getMessage());
                            return Mono.empty();
                        }))
                .count()
                .doOnNext(count -> log.info("[Push] {} suscriptores notificados. Segmento: {}",
                        count, bottleneck.segmentId()))
                .then();
    }

    private Mono<Void> sendPushNotification(PushSubscription sub, String body) {
        return Mono.fromCallable(() -> {
                    PushService pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
                    Subscription subscription = new Subscription(
                            sub.getEndpoint(),
                            new Subscription.Keys(sub.getP256dh(), sub.getAuth()));
                    Notification notification = new Notification(subscription, body);
                    pushService.send(notification);
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
