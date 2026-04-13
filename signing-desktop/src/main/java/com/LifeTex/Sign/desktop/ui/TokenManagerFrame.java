package com.lifetex.sign.desktop.ui;

import com.lifetex.sign.desktop.model.CertificateInfo;
import com.lifetex.sign.desktop.model.TokenProfile;
import com.lifetex.sign.desktop.service.TokenConfigService;
import com.lifetex.sign.desktop.service.UsbTokenService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class TokenManagerFrame extends JFrame {

    private final TokenConfigService configService;
    private final UsbTokenService usbTokenService;
    private JTable table;
    private DefaultTableModel tableModel;

    public TokenManagerFrame(TokenConfigService configService, UsbTokenService usbTokenService) {
        super("Quản lý USB Token");
        this.configService = configService;
        this.usbTokenService = usbTokenService;
        initUI();
        loadData();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        // Toolbar
        JToolBar toolBar = new JToolBar();
        JButton btnAdd = new JButton("Thêm");
        JButton btnEdit = new JButton("Sửa");
        JButton btnDelete = new JButton("Xóa");
        JButton btnCheck = new JButton("Kết nối & Xem Chứng thư");

        btnAdd.addActionListener(e -> addToken());
        btnEdit.addActionListener(e -> editToken());
        btnDelete.addActionListener(e -> deleteToken());
        btnCheck.addActionListener(e -> checkToken());

        toolBar.add(btnAdd);
        toolBar.add(btnEdit);
        toolBar.add(btnDelete);
        toolBar.addSeparator();
        toolBar.add(btnCheck);

        add(toolBar, BorderLayout.NORTH);

        // Table
        String[] columns = { "ID", "Tên cấu hình", "Đường dẫn DLL", "Slot" };
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        // Hide ID column
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);
        table.getColumnModel().getColumn(0).setWidth(0);

        add(new JScrollPane(table), BorderLayout.CENTER);

        setSize(700, 450);
        setLocationRelativeTo(null);

        // Set icon if available
        try {
            Image image = Toolkit.getDefaultToolkit().createImage(getClass().getResource("/icon.png"));
            setIconImage(image);
        } catch (Exception ignored) {
        }
    }

    private void loadData() {
        tableModel.setRowCount(0);
        List<TokenProfile> profiles = configService.getAllProfiles();
        for (TokenProfile p : profiles) {
            tableModel.addRow(new Object[] { p.getId(), p.getName(), p.getLibraryPath(), p.getSlotIndex() });
        }
    }

    private void addToken() {
        TokenProfileDialog dialog = new TokenProfileDialog(this, null);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            configService.saveProfile(dialog.getProfile());
            loadData();
        }
    }

    private void editToken() {
        int row = table.getSelectedRow();
        if (row < 0)
            return;
        String id = (String) tableModel.getValueAt(row, 0);

        configService.getProfile(id).ifPresent(p -> {
            TokenProfileDialog dialog = new TokenProfileDialog(this, p);
            dialog.setVisible(true);
            if (dialog.isConfirmed()) {
                configService.saveProfile(dialog.getProfile());
                loadData();
            }
        });
    }

    private void deleteToken() {
        int row = table.getSelectedRow();
        if (row < 0)
            return;
        String id = (String) tableModel.getValueAt(row, 0);
        String name = (String) tableModel.getValueAt(row, 1);

        if (JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn xóa cấu hình: " + name + "?",
                "Xác nhận", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            configService.deleteProfile(id);
            loadData();
        }
    }

    private void checkToken() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một cấu hình Token để kiểm tra.");
            return;
        }
        String id = (String) tableModel.getValueAt(row, 0);
        configService.getProfile(id).ifPresent(p -> {
            // Ask for PIN
            JPasswordField pf = new JPasswordField();
            int okCxl = JOptionPane.showConfirmDialog(this, pf, "Nhập mã PIN cho " + p.getName(),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (okCxl == JOptionPane.OK_OPTION) {
                String pin = new String(pf.getPassword());

                // Run check in thread
                new Thread(() -> {
                    // Show Loading
                    final LoadingDialog[] loading = { null };
                    try {
                        SwingUtilities.invokeAndWait(() -> {
                            loading[0] = new LoadingDialog(TokenManagerFrame.this, "Đang kết nối Token...");
                        });
                        SwingUtilities.invokeLater(() -> loading[0].setVisible(true));
                    } catch (Exception ignored) {
                    }

                    try {
                        List<CertificateInfo> certs = usbTokenService.getCertificates(pin, p.getLibraryPath(),
                                p.getSlotIndex());

                        // Success -> Close Loading & Show Data
                        SwingUtilities.invokeLater(() -> {
                            if (loading[0] != null)
                                loading[0].close();
                            if (certs.isEmpty()) {
                                JOptionPane.showMessageDialog(TokenManagerFrame.this,
                                        "Không tìm thấy chứng thư nào trong token.");
                            } else {
                                // Use rich dialog
                                new CertificateListDialog(TokenManagerFrame.this, certs).setVisible(true);
                            }
                        });
                    } catch (Exception e) {
                        log.error("Check failed", e);
                        // Error -> Close Loading & Show Error
                        SwingUtilities.invokeLater(() -> {
                            if (loading[0] != null)
                                loading[0].close();
                            JOptionPane.showMessageDialog(TokenManagerFrame.this, "Lỗi kiểm tra: " + e.getMessage(),
                                    "Lỗi", JOptionPane.ERROR_MESSAGE);
                        });
                    }
                }).start();
            }
        });
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TokenManagerFrame.class);
}
