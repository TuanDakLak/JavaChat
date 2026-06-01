package com.quangtuan.chat.client;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class ChatClientApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new MainFrame().setVisible(true);
        });
    }
}
