package com.lifetex.sign.desktop.service;

import com.lifetex.sign.desktop.model.CertificateInfo;
import com.lifetex.sign.desktop.ui.CertificateListDialog;
import com.lifetex.sign.desktop.ui.LoadingDialog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.awt.*;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenMonitorService {

    private final NotificationService notificationService;
    private final UsbTokenService usbTokenService;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        notificationService.setCheckTokenAction(this::manualCheck);
        log.info("Token Monitor initialized (Manual mode)");
    }

    private void manualCheck() {
        log.info("Thực hiện kiểm tra kết nối thủ công...");

        new Thread(() -> {
            try {
                String pin = showPinDialog();
                if (pin == null) {
                    log.info("Người dùng hủy nhập PIN");
                    return;
                }

                // Show Loading
                final LoadingDialog[] loading = { null };
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        loading[0] = new LoadingDialog(null, "Đang kết nối Token...");
                    });
                    SwingUtilities.invokeLater(() -> loading[0].setVisible(true));
                } catch (Exception ignored) {
                }

                List<CertificateInfo> certs;
                try {
                    certs = usbTokenService.getCertificates(pin, null, null);
                } catch (Exception e) {
                    log.error("Check connection failed", e);

                    // Close loading
                    SwingUtilities.invokeLater(() -> {
                        if (loading[0] != null)
                            loading[0].close();
                    });

                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.contains("CKR_PIN_INCORRECT")) {
                        errorMsg = "Mã PIN không chính xác";
                    }
                    notificationService.showNotification("Lỗi kết nối", "Lỗi: " + errorMsg, TrayIcon.MessageType.ERROR);
                    return;
                }

                // Close loading & Show Result
                SwingUtilities.invokeLater(() -> {
                    if (loading[0] != null)
                        loading[0].close();

                    if (certs != null && !certs.isEmpty()) {
                        // Display rich dialog
                        new CertificateListDialog(null, certs).setVisible(true);
                    } else {
                        notificationService.showNotification("Kết quả kiểm tra", "Token trống (không có chứng thư)",
                                TrayIcon.MessageType.WARNING);
                    }
                });

            } catch (Exception e) {
                log.error("Unexpected error", e);
            }
        }).start();
    }

    private String showPinDialog() {
        final String[] result = { null };
        try {
            SwingUtilities.invokeAndWait(() -> {
                JPasswordField pf = new JPasswordField();
                int okCxl = JOptionPane.showConfirmDialog(null, pf, "Nhập mã PIN USB Token",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (okCxl == JOptionPane.OK_OPTION) {
                    result[0] = new String(pf.getPassword());
                }
            });
        } catch (Exception e) {
            log.error("Error showing PIN dialog", e);
        }
        return result[0];
    }
}
