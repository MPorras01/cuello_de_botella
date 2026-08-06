package co.medellin.trancones.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita los intentos de inicio de sesión por IP para mitigar ataques de
 * fuerza bruta. Usa Redis (contador con TTL) y cae a memoria si Redis no
 * está disponible.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginRateLimiter {

    private static final String KEY_PREFIX = "login:rate:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @Value("${security.login.max-attempts:5}")
    private int maxAttempts;

    @Value("${security.login.window-minutes:15}")
    private long windowMinutes;

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    /**
     * Verifica que la IP no haya superado el límite de intentos.
     *
     * @throws TooManyAttemptsException si se supera el límite
     */
    public Mono<Void> check(String ip) {
        return redisCheck(ip)
                .onErrorResume(e -> {
                    log.warn("[Security] Redis no disponible para rate-limit, usando memoria: {}", e.getMessage());
                    return memoryCheck(ip);
                });
    }

    private Mono<Void> redisCheck(String ip) {
        String key = KEY_PREFIX + ip;
        Duration window = Duration.ofMinutes(windowMinutes);
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    if (count == 1L) {
                        return redisTemplate.expire(key, window).then();
                    }
                    if (count > maxAttempts) {
                        return Mono.error(new TooManyAttemptsException());
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> memoryCheck(String ip) {
        Instant now = Instant.now();
        Deque<Instant> deque = attempts.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty()
                    && deque.peekFirst().isBefore(now.minus(Duration.ofMinutes(windowMinutes)))) {
                deque.pollFirst();
            }
            if (deque.size() >= maxAttempts) {
                return Mono.error(new TooManyAttemptsException());
            }
            deque.addLast(now);
            return Mono.empty();
        }
    }
}
