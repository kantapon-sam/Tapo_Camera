package local.tapo.viewer;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.Border;

public final class UiTheme {
    private static final String THAI_SAMPLE = "หน้าร้าน กล้อง เคาน์เตอร์";
    private static final Font BASE_FONT = chooseBaseFont();

    public static final Color APP_BACKGROUND = new Color(242, 245, 248);
    public static final Color SURFACE = new Color(255, 255, 255);
    public static final Color SURFACE_ALT = new Color(247, 249, 251);
    public static final Color VIDEO_BACKGROUND = new Color(16, 20, 24);
    public static final Color BORDER = new Color(211, 218, 226);
    public static final Color TEXT = new Color(31, 41, 55);
    public static final Color TEXT_MUTED = new Color(94, 107, 124);
    public static final Color ACCENT = new Color(28, 105, 212);
    public static final Color ACCENT_DARK = new Color(19, 80, 166);
    public static final Color SUCCESS = new Color(35, 132, 85);
    public static final Color WARNING = new Color(178, 108, 0);
    public static final Color DANGER = new Color(192, 57, 57);

    private UiTheme() {
    }

    public static void installDefaults() {
        Font base = font(Font.PLAIN, 13f);
        UIManager.put("Button.font", base);
        UIManager.put("ComboBox.font", base);
        UIManager.put("Label.font", base);
        UIManager.put("List.font", base);
        UIManager.put("OptionPane.messageFont", base);
        UIManager.put("OptionPane.buttonFont", base);
        UIManager.put("Spinner.font", base);
        UIManager.put("TabbedPane.font", base);
        UIManager.put("TextField.font", base);
        UIManager.put("TextArea.font", base);
        UIManager.put("FormattedTextField.font", base);
        UIManager.put("ToolBar.background", SURFACE);
        UIManager.put("Panel.background", APP_BACKGROUND);
    }

    public static void styleRoot(JComponent component) {
        component.setBackground(APP_BACKGROUND);
    }

    public static void styleSurface(JComponent component) {
        component.setBackground(SURFACE);
        component.setBorder(panelBorder());
    }

    public static void styleControlStrip(JPanel panel) {
        panel.setBackground(SURFACE_ALT);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
    }

    public static void styleHeader(JPanel panel) {
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
    }

    public static JLabel cameraTitle(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(font(Font.BOLD, 14f));
        return label;
    }

    public static JLabel toolbarTitle(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(font(Font.BOLD, 18f));
        return label;
    }

    public static void stylePlainLabel(JLabel label) {
        label.setForeground(TEXT_MUTED);
    }

    public static void styleStatusLabel(JLabel label, String status) {
        label.setOpaque(true);
        label.setText(status);
        label.setFont(font(Font.BOLD, 12f));
        label.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        label.setForeground(Color.WHITE);

        if (status == null) {
            label.setBackground(TEXT_MUTED);
        } else if (status.contains("Live") || status.contains("Playing") || status.contains("Recording")) {
            label.setBackground(SUCCESS);
        } else if (status.contains("Error") || status.contains("failed") || status.contains("not found")) {
            label.setBackground(DANGER);
        } else if (status.contains("Connecting") || status.contains("Starting") || status.contains("Stopping")
            || status.contains("Opening")) {
            label.setBackground(WARNING);
        } else {
            label.setBackground(TEXT_MUTED);
        }
    }

    public static void styleButton(JButton button, ButtonKind kind) {
        Color accent;
        Color background;
        if (kind == ButtonKind.PRIMARY) {
            accent = ACCENT;
            background = new Color(226, 239, 255);
        } else if (kind == ButtonKind.DANGER) {
            accent = DANGER;
            background = new Color(255, 235, 235);
        } else if (kind == ButtonKind.SUCCESS) {
            accent = SUCCESS;
            background = new Color(226, 246, 236);
        } else if (kind == ButtonKind.WARNING) {
            accent = WARNING;
            background = new Color(255, 243, 220);
        } else {
            accent = new Color(128, 141, 158);
            background = new Color(232, 236, 241);
        }

        button.setFocusPainted(false);
        button.setForeground(TEXT);
        button.setBackground(background);
        button.setFont(font(Font.BOLD, 13f));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(buttonBorder(accent));
        button.setMargin(new Insets(5, 12, 5, 12));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public static Border panelBorder() {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)
        );
    }

    private static Border buttonBorder(Color color) {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)
        );
    }

    private static Font font(int style, float size) {
        return BASE_FONT.deriveFont(style, size);
    }

    private static Font chooseBaseFont() {
        String[] candidates = new String[] {
            "Tahoma",
            "Leelawadee UI",
            "Microsoft Sans Serif",
            Font.DIALOG
        };

        for (String candidate : candidates) {
            Font font = new Font(candidate, Font.PLAIN, 13);
            if (font.canDisplayUpTo(THAI_SAMPLE) == -1) {
                return font;
            }
        }
        return new Font(Font.DIALOG, Font.PLAIN, 13);
    }

    public enum ButtonKind {
        PRIMARY,
        SECONDARY,
        SUCCESS,
        WARNING,
        DANGER
    }
}
