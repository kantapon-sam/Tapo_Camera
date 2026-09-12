package local.tapo.viewer;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

final class AddCameraDialog {
    private AddCameraDialog() {
    }

    static CameraConfig show(Component parent, AppConfig config) {
        JPanel form = new JPanel(new GridBagLayout());
        JTextField host = new JTextField(24);
        host.setName("cameraHost");
        JTextField name = new JTextField(24);
        name.setName("cameraName");
        JSpinner port = new JSpinner(new SpinnerNumberModel(554, 1, 65535, 1));
        port.setEditor(new JSpinner.NumberEditor(port, "#"));
        JComboBox<String> stream = new JComboBox<>(new String[] {"stream1", "stream2"});
        JCheckBox reuseAccount = new JCheckBox("Use an existing camera account", !config.cameras().isEmpty());
        JComboBox<CameraConfig> account = new JComboBox<>(config.cameras().toArray(new CameraConfig[0]));
        account.setPrototypeDisplayValue(new CameraConfig("", "192.168.1.100/stream1", true,
            "", 554, "", "", "stream1", ""));
        if (account.getItemCount() > 0) {
            account.setSelectedIndex(account.getItemCount() - 1);
        }
        JTextField username = new JTextField(24);
        JPasswordField password = new JPasswordField(24);
        JLabel error = new JLabel(" ");
        error.setForeground(UiTheme.DANGER);
        reuseAccount.setEnabled(!config.cameras().isEmpty());
        Runnable updateAccountFields = () -> {
            account.setEnabled(reuseAccount.isSelected());
            username.setEnabled(!reuseAccount.isSelected());
            password.setEnabled(!reuseAccount.isSelected());
        };
        reuseAccount.addActionListener(event -> updateAccountFields.run());
        updateAccountFields.run();

        addRow(form, 0, "IP / Host", host);
        addRow(form, 1, "Name (optional)", name);
        addRow(form, 2, "RTSP port", port);
        addRow(form, 3, "Stream", stream);
        addRow(form, 4, "Account", reuseAccount);
        addRow(form, 5, "Copy account from", account);
        addRow(form, 6, "Username", username);
        addRow(form, 7, "Password", password);
        GridBagConstraints footer = new GridBagConstraints();
        footer.gridy = 8;
        footer.gridwidth = 2;
        footer.anchor = GridBagConstraints.WEST;
        footer.insets = new Insets(6, 4, 4, 4);
        form.add(error, footer);

        try {
            while (true) {
                int choice = JOptionPane.showOptionDialog(parent, form, "Add Camera",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                    new String[] {"Add Camera", "Cancel"}, "Add Camera");
                if (choice != 0) {
                    return null;
                }
                char[] secret = password.getPassword();
                try {
                    port.commitEdit();
                    String url = RtspUrlBuilder.forNewCamera(host.getText(), ((Number) port.getValue()).intValue(),
                        (String) stream.getSelectedItem(), username.getText(), new String(secret),
                        reuseAccount.isSelected() ? (CameraConfig) account.getSelectedItem() : null);
                    return ConfigLoader.addCamera(config.configPath(), url, name.getText());
                } catch (ParseException e) {
                    error.setText("Port must be between 1 and 65535.");
                } catch (IllegalArgumentException e) {
                    error.setText(e.getMessage());
                } catch (IOException e) {
                    error.setText("Could not save the camera. Check config file permissions.");
                    error.setToolTipText(config.configPath().toString());
                } finally {
                    Arrays.fill(secret, '\0');
                }
            }
        } finally {
            password.setText("");
        }
    }

    private static void addRow(JPanel form, int row, String caption, JComponent field) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(5, 4, 5, 12);
        JLabel label = new JLabel(caption);
        label.setLabelFor(field);
        form.add(label, labelConstraints);
        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = new Insets(5, 0, 5, 4);
        form.add(field, fieldConstraints);
    }
}
