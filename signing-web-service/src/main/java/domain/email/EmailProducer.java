package domain.email;

import com.lifetex.sign.model.EmailJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class EmailProducer {

    @Autowired
    private StringRedisTemplate redis;

    public EmailProducer(StringRedisTemplate redisTemplate) {
        this.redis = redisTemplate;
    }

    public void pushJob(EmailJob job) {
        Map<String, String> data = new HashMap<>();
        data.put("email", job.getEmail());
        data.put("otp", String.valueOf(job.getOtp()));
        data.put("retry", String.valueOf(job.getRetry()));
        data.put("timestamp", String.valueOf(job.getTimestamp()));

        RecordId recordId = redis.opsForStream().add("email_stream", data);

        System.out.println("✅ Message pushed to stream:");
        System.out.println("   - Stream ID: " + recordId);
        System.out.println("   - Email: " + job.getEmail());
        System.out.println("   - OTP: " + job.getOtp());
    }
}