package com.lifetex.sign.desktop.service;

import com.lifetex.sign.desktop.ui.TokenManagerFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

@Slf4j
@Service
public class NotificationService {

    private final TrayIcon trayIcon;
    private final TokenManagerFrame tokenManagerFrame;
    private Runnable checkTokenAction;

    // Services injected by Spring
    public NotificationService(TokenConfigService configService, UsbTokenService usbTokenService) {
        // Init Tray
        TrayIcon tray = null;
        if (SystemTray.isSupported()) {
            try {
                SystemTray systemTray = SystemTray.getSystemTray();
                Image image = Toolkit.getDefaultToolkit().createImage(getClass().getResource("/icon.png"));

                if (image == null) {
                    image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
                }

                tray = new TrayIcon(image, "Tool ký số LifeTex");
                tray.setImageAutoSize(true);
                tray.setToolTip("LifeTex Signing Service đang chạy");

                PopupMenu popup = new PopupMenu();

                MenuItem managerItem = new MenuItem("Quan ly Token");
                managerItem.addActionListener(e -> showTokenManager());
                popup.add(managerItem);

                MenuItem checkItem = new MenuItem("Kiem tra ket noi");
                checkItem.addActionListener(e -> {
                    if (checkTokenAction != null) {
                        checkTokenAction.run();
                    }
                });
                popup.add(checkItem);
                popup.addSeparator();

                MenuItem exitItem = new MenuItem("Thoat");
                exitItem.addActionListener(e -> System.exit(0));
                popup.add(exitItem);
                tray.setPopupMenu(popup);

                systemTray.add(tray);
            } catch (Exception e) {
                log.warn("Lỗi không thể thông báo: ", e);
            }
        } else {
            log.info("Hệ thông không hỗ trợ thông báo trên khay hệ thống.");
        }
        this.trayIcon = tray;

        // Init Manager UI
        TokenManagerFrame frame = null;
        try {
            frame = new TokenManagerFrame(configService, usbTokenService);
        } catch (Exception e) {
            log.error("Failed to init TokenManagerFrame", e);
        }
        this.tokenManagerFrame = frame;
    }

    private void showTokenManager() {
        if (tokenManagerFrame != null) {
            SwingUtilities.invokeLater(() -> {
                tokenManagerFrame.setVisible(true);
                tokenManagerFrame.toFront();
            });
        }
    }

    public void setCheckTokenAction(Runnable action) {
        this.checkTokenAction = action;
    }

    public void showNotification(String title, String message, TrayIcon.MessageType type) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, type);
        }
    }

    public void showErrorDialog(String message) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            JOptionPane.showMessageDialog(null, message, "Signing Service Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ignored) {
            System.err.println(message);
        }
    }
}
