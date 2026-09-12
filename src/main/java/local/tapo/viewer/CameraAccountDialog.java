package local.tapo.viewer;

import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

final class CameraAccountDialog {
    private CameraAccountDialog() {
    }

    static void show(Component parent, CameraConfig camera) {
        CameraCredentials credentials;
        try {
            credentials = CameraCredentials.from(camera);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(parent, "The camera account could not be read.",
                "User / Pass", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JTextField username = new JTextField(credentials.username(), 28);
        username.setEditable(false);
        username.setName("accountUsername");
        JPasswordField password = new JPasswordField(credentials.password(), 28);
        password.setEditable(false);
        password.setName("accountPassword");
        char echo = password.getEchoChar();
        JCheckBox show = new JCheckBox("Show password");
        show.setName("showAccountPassword");
        show.addActionListener(event -> password.setEchoChar(show.isSelected() ? '\0' : echo));
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 6));
        form.add(new JLabel("Username"));
        form.add(username);
        form.add(new JLabel("Password"));
        form.add(password);
        form.add(show);
        if (credentials.username().isEmpty() && credentials.password().isEmpty()) {
            form.add(new JLabel("No account is set for this camera."));
        }
        try {
            JOptionPane.showMessageDialog(parent, form, "User / Pass - " + camera.displayName(),
                JOptionPane.PLAIN_MESSAGE);
        } finally {
            password.setText("");
            username.setText("");
        }
    }
}
