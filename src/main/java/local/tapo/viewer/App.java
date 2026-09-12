package local.tapo.viewer;

import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Keep the default look and feel if the system one is unavailable.
        }
        UiTheme.installDefaults();

        Path configPath = args.length > 0 ? Paths.get(args[0]) : Paths.get("config", "cameras.properties");

        try {
            AppConfig config = ConfigLoader.load(configPath);
            boolean vlcFound = new NativeDiscovery().discover();

            SwingUtilities.invokeLater(() -> {
                ViewerFrame frame = new ViewerFrame(config, vlcFound);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                if (config.liveAutoStart()) {
                    frame.playAll();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                null,
                e.getMessage(),
                "Tapo RTSP Viewer startup failed",
                JOptionPane.ERROR_MESSAGE
            ));
        }
    }
}
