package com.lifetex.sign.desktop;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.HttpHeaders;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.filter.CorsFilter;

import com.formdev.flatlaf.FlatLightLaf;
import com.lifetex.sign.desktop.service.NotificationService;

import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

@Slf4j
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class, SecurityAutoConfiguration.class })
@ComponentScan(basePackages = {
        "com.lifetex.sign.desktop",
        "com.lifetex.sign.service.core",
        "com.lifetex.sign.service.dss_custom"
})
public class SigningDesktopApplication {

    private final NotificationService notificationService;

    public SigningDesktopApplication(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        try {
            FlatLightLaf.setup();

            SpringApplication.run(SigningDesktopApplication.class, args);
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                JOptionPane.showMessageDialog(null, "Khởi động ký số thất bại :\n" + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("Khởi động ứng dụng ký số thành công qua USB Token Service.");
        notificationService.showNotification("Ứng dụng ký số đã khởi động",
                " Khởi động thành công ", java.awt.TrayIcon.MessageType.INFO);
    }
}
