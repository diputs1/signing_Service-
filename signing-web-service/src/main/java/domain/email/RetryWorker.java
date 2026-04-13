package domain.email;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class RetryWorker {

    @Autowired
    private StringRedisTemplate redis;

    private final String RETRY_STREAM = "email_retry_stream";
    private final String RETRY_GROUP = "retry_group";
    private final String CONSUMER_NAME = "retry-worker-" + UUID.randomUUID().toString();

    @PostConstruct
    public void start() {
        // Tạo consumer group
        try {
            redis.opsForStream().createGroup(RETRY_STREAM, ReadOffset.from("0"), RETRY_GROUP);
        } catch (Exception e) {
            // Group đã tồn tại
        }

        new Thread(this::run, "retry-worker").start();
    }

    private void run() {
        while (true) {
            try {
                List<MapRecord<String, Object, Object>> messages = redis.opsForStream().read(
                        Consumer.from(RETRY_GROUP, CONSUMER_NAME), // Consumer cố định
                        StreamReadOptions.empty().block(Duration.ofSeconds(3)),
                        StreamOffset.create(RETRY_STREAM, ReadOffset.lastConsumed()));

                if (messages == null)
                    continue;

                for (var msg : messages) {
                    retry(msg);
                }

            } catch (Exception e) {
                e.printStackTrace();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void retry(MapRecord<String, Object, Object> msg) {
        int retry = Integer.parseInt(msg.getValue().get("retry").toString());

        try {
            // Exponential backoff: 2^retry giây
            long delayMs = (long) (Math.pow(2, retry) * 1000);
            System.out.println("⏳ Waiting " + delayMs + "ms before retry " + retry);
            Thread.sleep(delayMs);

            // Push lại vào main stream
            Map<String, String> retryData = new HashMap<>();
            retryData.put("email", msg.getValue().get("email").toString());
            retryData.put("otp", msg.getValue().get("otp").toString());
            retryData.put("retry", String.valueOf(retry));
            retryData.put("timestamp", String.valueOf(System.currentTimeMillis()));

            redis.opsForStream().add("email_stream", retryData);
            redis.opsForStream().acknowledge(RETRY_STREAM, RETRY_GROUP, msg.getId());

            System.out.println("Message pushed back to main stream");

        } catch (Exception e) {
            System.err.println("Retry failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}