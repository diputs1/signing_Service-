package domain.email;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class EmailWorker {

    private static final Logger log = LoggerFactory.getLogger(EmailWorker.class);

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private EmailSenderService emailSender;

    private final String STREAM = "email_stream";
    private final String GROUP = "email_group";

    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread workerThread;

    @PostConstruct
    public void start() {
        workerThread = new Thread(this::run, "EmailWorker");
        workerThread.setDaemon(true);
        workerThread.start();
        log.info("EmailWorker started");
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
        }
        log.info("EmailWorker stopped");
    }

    private void run() {
        int consecutiveErrors = 0;
        final int MAX_CONSECUTIVE_ERRORS = 10;

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, Object, Object>> messages = redis.opsForStream().read(
                        Consumer.from(GROUP, "worker-" + UUID.randomUUID()),
                        StreamReadOptions.empty().block(Duration.ofSeconds(3)),
                        StreamOffset.create(STREAM, ReadOffset.lastConsumed()));

                consecutiveErrors = 0; // Reset on success

                if (messages == null)
                    continue;

                for (var msg : messages) {
                    if (!running.get())
                        break;
                    process(msg);
                }

            } catch (IllegalStateException e) {
                // Connection factory destroyed - stop the worker
                log.warn("Redis connection destroyed, stopping EmailWorker: {}", e.getMessage());
                running.set(false);
                break;
            } catch (Exception e) {
                consecutiveErrors++;
                log.error("EmailWorker error ({}/{}): {}", consecutiveErrors, MAX_CONSECUTIVE_ERRORS, e.getMessage());

                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    log.error("Too many consecutive errors, stopping EmailWorker");
                    running.set(false);
                    break;
                }

                // Backoff before retry
                try {
                    Thread.sleep(Math.min(1000 * consecutiveErrors, 10000));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.info("EmailWorker thread exiting");
    }

    private void process(MapRecord<String, Object, Object> msg) {

        String email = msg.getValue().get("email").toString();
        String otp = msg.getValue().get("otp").toString();
        int retry = Integer.parseInt(msg.getValue().get("retry").toString());

        try {
            emailSender.send(email, otp);
            redis.opsForStream().acknowledge(STREAM, GROUP, msg.getId());
        } catch (Exception ex) {
            log.error("Failed to send email to {}: {}", email, ex.getMessage());
            handleFailure(msg, retry);
        }
    }

    private void handleFailure(MapRecord<String, Object, Object> msg, int retry) {

        if (retry >= 3) {
            // DLQ
            redis.opsForStream().add("email_dead_letter", msg.getValue());
        } else {
            // Push sang retry stream
            redis.opsForStream().add("email_retry_stream", Map.of(
                    "email", msg.getValue().get("email"),
                    "otp", msg.getValue().get("otp"),
                    "retry", retry + 1));
        }

        redis.opsForStream().acknowledge(STREAM, GROUP, msg.getId());
    }
}
