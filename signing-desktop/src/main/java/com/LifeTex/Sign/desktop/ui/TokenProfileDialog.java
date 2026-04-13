package com.lifetex.sign.desktop.ui;

import com.lifetex.sign.desktop.model.TokenProfile;

import javax.swing.*;
import java.awt.*;

public class TokenProfileDialog extends JDialog {

    private JTextField txtName;
    private JTextField txtPath;
    private JSpinner spnSlot;
    private boolean confirmed = false;
    private TokenProfile profile;

    public TokenProfileDialog(Frame owner, TokenProfile profile) {
        super(owner, "Cấu hình Token", true);
        this.profile = profile != null ? profile : new TokenProfile();
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        JPanel form = new JPanel(new GridLayout(3, 2, 5, 5));
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        form.add(new JLabel("Tên cấu hình:"));
        txtName = new JTextField(profile.getName());
        form.add(txtName);

        form.add(new JLabel("Đường dẫn DLL:"));
        JPanel pathPanel = new JPanel(new BorderLayout(5, 0));
        txtPath = new JTextField(profile.getLibraryPath());
        JButton btnBrowse = new JButton("...");
        btnBrowse.addActionListener(e -> browseFile());
        pathPanel.add(txtPath, BorderLayout.CENTER);
        pathPanel.add(btnBrowse, BorderLayout.EAST);
        form.add(pathPanel);

        form.add(new JLabel("Slot Index:"));
        spnSlot = new JSpinner(new SpinnerNumberModel(profile.getSlotIndex(), 0, 100, 1));
        JPanel slotPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        slotPanel.add(spnSlot);
        form.add(slotPanel);

        add(form, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSave = new JButton("Lưu");
        JButton btnCancel = new JButton("Hủy");

        btnSave.addActionListener(e -> {
            profile.setName(txtName.getText().trim());
            profile.setLibraryPath(txtPath.getText().trim());
            profile.setSlotIndex((Integer) spnSlot.getValue());
            confirmed = true;
            dispose();
        });

        btnCancel.addActionListener(e -> dispose());
        btnPanel.add(btnSave);
        btnPanel.add(btnCancel);

        add(btnPanel, BorderLayout.SOUTH);

        setSize(400, 200);
        setLocationRelativeTo(getOwner());
    }

    private void browseFile() {
        JFileChooser fc = new JFileChooser();
        if (txtPath.getText() != null && !txtPath.getText().isEmpty()) {
            fc.setSelectedFile(new java.io.File(txtPath.getText()));
        }
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            txtPath.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public TokenProfile getProfile() {
        return profile;
    }
}
