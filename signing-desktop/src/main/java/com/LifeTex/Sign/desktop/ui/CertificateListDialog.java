package com.lifetex.sign.desktop.ui;

import com.lifetex.sign.desktop.model.CertificateInfo;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;

public class CertificateListDialog extends JDialog {

    public CertificateListDialog(Window owner, List<CertificateInfo> certificates) {
        super(owner, "Danh sách Chứng thư số", ModalityType.APPLICATION_MODAL);
        initUI(certificates);
    }

    private void initUI(List<CertificateInfo> certificates) {
        setLayout(new BorderLayout(15, 15));

        // Header Panel (Optional)
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        JLabel lblTitle = new JLabel("Tìm thấy " + certificates.size() + " chứng thư số trong Token");
        lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 14f));
        lblTitle.setForeground(new Color(0, 102, 204)); // Blue Accent
        headerPanel.add(lblTitle, BorderLayout.WEST);
        add(headerPanel, BorderLayout.NORTH);

        // Table Data
        String[] columns = { "Chủ sở hữu (Subject)", "Nhà cung cấp (Issuer)", "Hạn sử dụng", "Số Serial" };
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

        for (CertificateInfo cert : certificates) {
            String expiry = (cert.getNotAfter() != null) ? sdf.format(cert.getNotAfter()) : "N/A";
            model.addRow(new Object[] {
                    cert.getSubject(),
                    cert.getIssuer(),
                    expiry,
                    cert.getSerialNumber()
            });
        }

        JTable table = new JTable(model);
        table.setRowHeight(25);
        table.setShowGrid(false); // Clean Look
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Custom Renderer for Padding
        DefaultTableCellRenderer paddedRenderer = new DefaultTableCellRenderer();
        paddedRenderer.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(paddedRenderer);
        }

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Outer Padding
        add(scrollPane, BorderLayout.CENTER);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnClose = new JButton("Đóng");
        btnClose.addActionListener(e -> dispose());
        btnPanel.add(btnClose);

        add(btnPanel, BorderLayout.SOUTH);

        setSize(700, 400); // Wider for 4 columns
        setLocationRelativeTo(getOwner());
    }
}
