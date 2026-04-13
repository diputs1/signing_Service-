package domain.email;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class AutoClaimWorker {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String STREAM = "email_stream";
    private static final String GROUP = "email_group";
    private static final String CONSUMER = "auto-claimer";

    @PostConstruct
    public void start() {
        new Thread(this::loop, "auto-claim-worker").start();
    }

    private void loop() {
        while (true) {
            try {
                // Lấy pending messages
                PendingMessages pending = redisTemplate.opsForStream()
                        .pending(STREAM, Consumer.from(GROUP, CONSUMER), Range.unbounded(), 1000L); // ← Thêm 2 tham số
                                                                                                    // này

                if (!pending.isEmpty()) {
                    System.out.println("Found pending: " + pending.size());

                    // Claim lại các messages pending quá 1 phút
                    List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream()
                            .claim(STREAM, GROUP, CONSUMER, Duration.ofMinutes(1));

                    if (claimed != null && !claimed.isEmpty()) {
                        System.out.println("Auto-claimed: " + claimed.size());

                        claimed.forEach(msg -> {
                            System.out.println("Processing: " + msg.getId());
                            // TODO: Xử lý gửi email

                            // ACK sau khi xong
                            redisTemplate.opsForStream().acknowledge(STREAM, GROUP, msg.getId());
                        });
                    }
                }

                Thread.sleep(5000);

            } catch (Exception ex) {
                ex.printStackTrace();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}