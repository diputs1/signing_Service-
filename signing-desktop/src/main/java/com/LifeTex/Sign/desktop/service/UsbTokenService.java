package com.lifetex.sign.desktop.service;

import com.lifetex.sign.desktop.model.CertificateInfo;
import com.lifetex.sign.model.dto.SignRequestDTO;
import com.lifetex.sign.service.core.PdfSigner;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.token.AbstractKeyStoreTokenConnection;
import eu.europa.esig.dss.token.AbstractSignatureTokenConnection;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.awt.*;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsbTokenService {

    private final PdfSigner pdfSigner;

    @Value("${signing.pkcs11.library:C:\\Windows\\System32\\hiloca_csp11_v1.dll}")
    private String pkcs11LibPath;

    public byte[] signPdf(byte[] pdfBytes, SignRequestDTO request, String pin, String customPkcs11Path)
            throws Exception {
        log.info("Bắt đầu ký số với USB Token. File size: {} bytes", pdfBytes.length);

        String finalLibPath = (customPkcs11Path != null && !customPkcs11Path.isBlank())
                ? customPkcs11Path
                : this.pkcs11LibPath;
        Integer slotIndex = 0;
        if (request instanceof com.lifetex.sign.desktop.controller.LocalSignRequestDTO) {
            slotIndex = ((com.lifetex.sign.desktop.controller.LocalSignRequestDTO) request).getSlotIndex();
            if (slotIndex == null)
                slotIndex = 0;
        }

        log.info("Library Path sử dụng: {}, SlotIndex: {}", finalLibPath, slotIndex);

        AbstractSignatureTokenConnection connection = null;
        Provider provider = null;
        try {
            // 1. Load Provider
            provider = loadPkcs11Provider(finalLibPath, slotIndex);

            if (Security.getProvider(provider.getName()) == null) {
                Security.addProvider(provider);
            }

            // 2. Load KeyStore
            KeyStore keyStore = loadKeyStore(provider, pin);

            // 3. Create Connection
            connection = new Pkcs11WrapperToken(keyStore, new KeyStore.PasswordProtection(pin.toCharArray()));

            // 4. Get Keys
            List<DSSPrivateKeyEntry> keys = connection.getKeys();
            if (keys.isEmpty()) {
                throw new RuntimeException("Không tìm thấy chứng thư số trong USB Token");
            }

            // 5. Select Certificate
            DSSPrivateKeyEntry keyEntry;
            if (keys.size() > 1) {
                keyEntry = showCertificateSelectionDialog(keys);
                if (keyEntry == null) {
                    throw new RuntimeException("Người dùng đã hủy chọn chứng thư");
                }
            } else {
                keyEntry = keys.get(0);
            }

            String subjectName = "Unknown";
            try {
                subjectName = keyEntry.getCertificate().getSubject().toString();
            } catch (Exception e) {
            }
            log.info("Sử dụng chứng thư: {}", subjectName);

            // 6. Sign
            DSSDocument toSignDocument = new InMemoryDocument(pdfBytes, "document.pdf");
            return pdfSigner.signPdf(toSignDocument, connection, keyEntry, request, "PAdES_BASELINE_B", "SHA256");

        } catch (Exception e) {
            log.error("Lỗi khi ký số: ", e);
            throw new RuntimeException("Lỗi ký số: " + e.getMessage());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) {
                }
            }
            if (provider != null) {
                try {
                    provider.clear();
                    Security.removeProvider(provider.getName());
                } catch (Exception ignored) {
                }
            }
        }
    }

    public List<CertificateInfo> getCertificates(String pin, String pkcs11Path, Integer slotIndex) throws Exception {
        String finalLibPath = (pkcs11Path != null && !pkcs11Path.isBlank()) ? pkcs11Path : this.pkcs11LibPath;
        Provider provider = null;
        AbstractSignatureTokenConnection connection = null;
        try {
            provider = loadPkcs11Provider(finalLibPath, slotIndex != null ? slotIndex : 0);
            if (Security.getProvider(provider.getName()) == null) {
                Security.addProvider(provider);
            }
            KeyStore keyStore = loadKeyStore(provider, pin);
            connection = new Pkcs11WrapperToken(keyStore, new KeyStore.PasswordProtection(pin.toCharArray()));
            List<DSSPrivateKeyEntry> keys = connection.getKeys();

            List<CertificateInfo> certInfos = new ArrayList<>();
            for (DSSPrivateKeyEntry key : keys) {
                CertificateToken token = key.getCertificate();
                String subject = getSubjectCommonName(token);
                String issuer = "Unknown";
                try {
                    issuer = token.getIssuer().getPrincipal().getName();
                    // Simplify Issuer (take CN only if possible)
                    if (issuer.contains("CN=")) {
                        int start = issuer.indexOf("CN=");
                        int end = issuer.indexOf(",", start);
                        if (end == -1)
                            end = issuer.length();
                        issuer = issuer.substring(start + 3, end);
                    }
                } catch (Exception e) {
                }
                Date notAfter = token.getNotAfter();
                String serial = token.getSerialNumber().toString(); // Corrected for DSS 5.x

                certInfos.add(new CertificateInfo(subject, issuer, notAfter, serial));
            }
            return certInfos;
        } finally {
            if (connection != null)
                try {
                    connection.close();
                } catch (Exception e) {
                }
            if (provider != null) {
                try {
                    provider.clear();
                    Security.removeProvider(provider.getName());
                } catch (Exception ignored) {
                }
            }
        }
    }

    private KeyStore loadKeyStore(Provider provider, String pin) throws Exception {
        KeyStore keyStore = null;
        try {
            keyStore = KeyStore.getInstance("PKCS11", provider);
        } catch (Exception ex) {
            log.warn("Failed to get PKCS11 KeyStore from specific provider instance: {}", ex.getMessage());
            try {
                keyStore = KeyStore.getInstance("PKCS11");
                log.info("Success getting PKCS11 KeyStore via generic lookup (system default)");
            } catch (Exception ex2) {
                log.error("Failed generic PKCS11 lookup too", ex2);
                throw ex;
            }
        }
        log.info("Đang đăng nhập vào Token...");
        keyStore.load(null, pin.toCharArray());
        return keyStore;
    }

    private String getSubjectCommonName(CertificateToken token) {
        try {
            String dn = token.getSubject().getPrincipal().getName();
            if (dn.contains("CN=")) {
                int start = dn.indexOf("CN=");
                int end = dn.indexOf(",", start);
                if (end == -1)
                    end = dn.length();
                return dn.substring(start + 3, end);
            }
            return dn;
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private DSSPrivateKeyEntry showCertificateSelectionDialog(List<DSSPrivateKeyEntry> keys) {
        final DSSPrivateKeyEntry[] selected = { null };

        try {
            SwingUtilities.invokeAndWait(() -> {
                JDialog dialog = new JDialog((Frame) null, "Chọn chứng thư số", true);
                dialog.setLayout(new BorderLayout(10, 10));

                String[] columnNames = { "Chủ sở hữu", "Nhà cung cấp", "Hạn dùng" };
                Object[][] data = new Object[keys.size()][3];
                for (int i = 0; i < keys.size(); i++) {
                    CertificateToken cert = keys.get(i).getCertificate();
                    data[i][0] = getSubjectCommonName(cert);
                    try {
                        String issuer = cert.getIssuer().getPrincipal().getName();
                        if (issuer.contains("CN=")) {
                            int start = issuer.indexOf("CN=");
                            int end = issuer.indexOf(",", start);
                            if (end == -1)
                                end = issuer.length();
                            issuer = issuer.substring(start + 3, end);
                        }
                        data[i][1] = issuer;
                    } catch (Exception e) {
                        data[i][1] = "";
                    }
                    data[i][2] = cert.getNotAfter();
                }

                JTable table = new JTable(data, columnNames);
                table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                if (keys.size() > 0)
                    table.setRowSelectionInterval(0, 0);

                JScrollPane scrollPane = new JScrollPane(table);
                dialog.add(scrollPane, BorderLayout.CENTER);

                JPanel btnPanel = new JPanel();
                JButton btnOk = new JButton("Chọn");
                JButton btnCancel = new JButton("Hủy");

                btnOk.addActionListener(e -> {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        selected[0] = keys.get(row);
                        dialog.dispose();
                    }
                });

                btnCancel.addActionListener(e -> dialog.dispose());

                btnPanel.add(btnOk);
                btnPanel.add(btnCancel);
                dialog.add(btnPanel, BorderLayout.SOUTH);

                dialog.setSize(600, 300);
                dialog.setLocationRelativeTo(null);
                dialog.setAlwaysOnTop(true);
                dialog.setVisible(true);
            });
        } catch (Exception e) {
            log.error("Error showing selection dialog", e);
        }

        return selected[0];
    }

    private Provider loadPkcs11Provider(String libPath, int slotIndex) {
        Exception lastError = null;

        try {
            String config = "--name=LifeTexToken\nlibrary = " + libPath;
            config += "\nslotListIndex = " + slotIndex;
            log.info("Trying PKCS11 config with slotListIndex={}", slotIndex);

            Provider prototype = Security.getProvider("SunPKCS11");
            if (prototype == null)
                throw new RuntimeException("SunPKCS11 provider missing");
            return prototype.configure(config);
        } catch (Exception e) {
            log.warn("Failed to load PKCS11 with slotListIndex={}: {}", slotIndex, e.getMessage());
            lastError = e;

            if (e.getMessage() != null && e.getMessage().contains("slotListIndex")) {
                log.info("Detected slot index error, will retry with slot 0 and auto-select");
            }
        }

        if (slotIndex != 0) {
            try {
                String config = "--name=LifeTexTokenSlot0\nlibrary = " + libPath;
                config += "\nslotListIndex = 0";
                log.info("Retrying PKCS11 config with slotListIndex=0");

                Provider prototype = Security.getProvider("SunPKCS11");
                if (prototype == null)
                    throw new RuntimeException("SunPKCS11 provider missing");
                return prototype.configure(config);
            } catch (Exception e) {
                log.warn("Failed to load PKCS11 with slotListIndex=0: {}", e.getMessage());
                if (lastError == null)
                    lastError = e;
            }
        }

        try {
            String config = "--name=LifeTexTokenAuto\nlibrary = " + libPath;
            log.info("Retrying PKCS11 config without slotListIndex (auto-select)");

            Provider prototype = Security.getProvider("SunPKCS11");
            if (prototype == null)
                throw new RuntimeException("SunPKCS11 provider missing");
            return prototype.configure(config);
        } catch (Exception e) {
            log.warn("Failed to load PKCS11 without slot index: {}", e.getMessage());
            if (lastError == null)
                lastError = e;
        }

        throw new RuntimeException("Không thể load thư viện PKCS11 sau các lần thử. Lỗi cuối: "
                + (lastError != null ? lastError.getMessage() : "Unknown"), lastError);
    }

    private static class Pkcs11WrapperToken extends AbstractKeyStoreTokenConnection {
        private final KeyStore keyStore;
        private final KeyStore.PasswordProtection passwordProtection;

        public Pkcs11WrapperToken(KeyStore keyStore, KeyStore.PasswordProtection passwordProtection) {
            this.keyStore = keyStore;
            this.passwordProtection = passwordProtection;
        }

        @Override
        public KeyStore getKeyStore() {
            return keyStore;
        }

        @Override
        protected KeyStore.PasswordProtection getKeyProtectionParameter() {
            return passwordProtection;
        }

        @Override
        public void close() {
        }
    }
}
