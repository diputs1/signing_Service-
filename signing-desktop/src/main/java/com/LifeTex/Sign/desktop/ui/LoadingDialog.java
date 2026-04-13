package com.lifetex.sign.desktop.ui;

import javax.swing.*;
import java.awt.*;

public class LoadingDialog extends JDialog {

    public LoadingDialog(Window owner, String message) {
        super(owner, "Please wait", ModalityType.APPLICATION_MODAL);
        initUI(message);
    }

    private void initUI(String message) {
        setLayout(new BorderLayout(20, 20));

        JPanel content = new JPanel(new BorderLayout(15, 15));
        content.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        // Icon (Optional - if we had a loading gif, but we use ProgressBar)

        JLabel lblMessage = new JLabel(message, SwingConstants.CENTER);
        lblMessage.setFont(lblMessage.getFont().deriveFont(Font.BOLD, 14f));

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(200, 6)); // Thinner

        content.add(lblMessage, BorderLayout.NORTH);
        content.add(progressBar, BorderLayout.CENTER);

        add(content, BorderLayout.CENTER);

        // FlatLaf style will handle the rounded corners and shadow roughly
        setUndecorated(true);
        ((JPanel) getContentPane()).setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1));

        pack(); // Auto size based on content
        setSize(300, getHeight() + 20); // Force width
        setLocationRelativeTo(getOwner());
    }

    public void close() {
        setVisible(false);
        dispose();
    }
}
