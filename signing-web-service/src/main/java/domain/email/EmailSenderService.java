package domain.email;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import static com.lifetex.sign.service.EnhancedOTPService.OTP_EXPIRY_MINUTES;

@Slf4j
@Service
public class EmailSenderService {

    @Autowired
    private JavaMailSender mailSender;

    public void send(String email, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject("🔐 Mã OTP xác thực ký PDF");

            String html = """
                     <div style="font-family: Arial, sans-serif; background:#f4f4f7; padding:20px;">
                         <div style="max-width:600px; margin:0 auto; background:white; border-radius:8px; padding:25px; box-shadow:0 2px 6px rgba(0,0,0,0.1);">
                            \s
                             <h2 style="color:#333; text-align:center; margin-top:0;">
                                 Xác thực hành động ký PDF
                             </h2>
                            \s
                             <p style="font-size:15px; color:#555;">
                                 Chúng tôi đã nhận được yêu cầu ký tài liệu PDF của bạn.
                             </p>

                             <p style="font-size:15px; color:#555;">
                                 Vui lòng sử dụng mã OTP bên dưới để hoàn tất quá trình xác thực:
                             </p>

                             <div style="text-align:center; margin:25px 0;">
                                 <span style="
                                     display:inline-block;
                                     font-size:32px;
                                     font-weight:bold;
                                     letter-spacing:6px;
                                     padding:15px 25px;
                                     background:#f0f4ff;
                                     color:#2b4eff;
                                     border-radius:8px;
                                     border:1px solid #d7e2ff;
                                 ">
                                     %s
                                 </span>
                             </div>

                             <p style="font-size:14px; color:#555;">
                                 ⌛ <strong>Mã có hiệu lực trong %d phút.</strong>
                             </p>

                             <p style="font-size:14px; color:#777;">
                                 Nếu bạn không yêu cầu mã này, hãy bỏ qua email này.
                             </p>

                             <hr style="margin:20px 0; border:none; border-top:1px solid #e0e0e0;" />

                             <p style="font-size:12px; color:#999; text-align:center;">
                                 Email được gửi tự động từ hệ thống ký số LifeTex. \s
                                 Vui lòng không trả lời email này.
                             </p>
                         </div>
                     </div>
                    \s"""
                    .formatted(otp, OTP_EXPIRY_MINUTES);

            helper.setText(html, true);
            mailSender.send(message);

        } catch (Exception e) {
            log.info("Loi khi gui email toi {}", email);
            e.printStackTrace();
        }
    }

}
