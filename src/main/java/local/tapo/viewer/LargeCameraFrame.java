package local.tapo.viewer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

public final class LargeCameraFrame extends JFrame {
    private final CameraConfig camera;
    private final String[] vlcOptions;
    private final EmbeddedMediaPlayerComponent playerComponent;
    private final JLabel titleLabel;
    private final JLabel statusLabel;
    private final JButton muteButton;
    private boolean released;
    private boolean muted;

    public LargeCameraFrame(CameraConfig camera, String[] vlcOptions, boolean muted) {
        super("Live - " + camera.displayName());
        this.camera = camera;
        this.vlcOptions = vlcOptions.clone();
        this.playerComponent = new EmbeddedMediaPlayerComponent();
        this.titleLabel = UiTheme.cameraTitle(camera.displayName());
        this.statusLabel = new JLabel("Stopped");
        this.muteButton = new JButton(muted ? "Unmute" : "Mute");
        this.muted = muted;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1180, 760);
        setLayout(new BorderLayout());
        getContentPane().setBackground(UiTheme.APP_BACKGROUND);

        JPanel header = new JPanel(new BorderLayout());
        UiTheme.styleHeader(header);
        header.add(titleLabel, BorderLayout.WEST);
        UiTheme.styleStatusLabel(statusLabel, "Stopped");
        header.add(statusLabel, BorderLayout.EAST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        UiTheme.styleControlStrip(controls);
        JButton playButton = new JButton("Live");
        JButton stopButton = new JButton("Stop");
        JButton closeButton = new JButton("Close");
        UiTheme.styleButton(playButton, UiTheme.ButtonKind.SUCCESS);
        UiTheme.styleButton(stopButton, UiTheme.ButtonKind.DANGER);
        UiTheme.styleButton(muteButton, muted ? UiTheme.ButtonKind.WARNING : UiTheme.ButtonKind.SECONDARY);
        UiTheme.styleButton(closeButton, UiTheme.ButtonKind.SECONDARY);
        controls.add(playButton);
        controls.add(stopButton);
        controls.add(muteButton);
        controls.add(closeButton);

        playerComponent.setBackground(UiTheme.VIDEO_BACKGROUND);
        add(header, BorderLayout.NORTH);
        add(playerComponent, BorderLayout.CENTER);
        add(controls, BorderLayout.SOUTH);

        playButton.addActionListener(event -> play());
        stopButton.addActionListener(event -> stop());
        muteButton.addActionListener(event -> setMuted(!this.muted));
        closeButton.addActionListener(event -> dispose());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                release();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                release();
            }
        });

        playerComponent.mediaPlayer().events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void playing(MediaPlayer mediaPlayer) {
                applyMuted();
                setStatus("Live");
            }

            @Override
            public void stopped(MediaPlayer mediaPlayer) {
                setStatus("Stopped");
            }

            @Override
            public void error(MediaPlayer mediaPlayer) {
                setStatus("Error");
            }
        });
    }

    public void play() {
        if (released) {
            return;
        }
        setStatus("Connecting...");
        playerComponent.mediaPlayer().media().play(camera.liveUrl(), vlcOptions);
        applyMuted();
    }

    public void stop() {
        playerComponent.mediaPlayer().controls().stop();
    }

    public void release() {
        if (released) {
            return;
        }
        released = true;
        stop();
        playerComponent.release();
    }

    public boolean isMuted() {
        return muted;
    }

    public CameraConfig camera() {
        return camera;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        applyMuted();
        SwingUtilities.invokeLater(() -> {
            muteButton.setText(muted ? "Unmute" : "Mute");
            UiTheme.styleButton(muteButton, muted ? UiTheme.ButtonKind.WARNING : UiTheme.ButtonKind.SECONDARY);
        });
    }

    public void refreshCameraName() {
        SwingUtilities.invokeLater(() -> {
            setTitle("Live - " + camera.displayName());
            titleLabel.setText(camera.displayName());
        });
    }

    private void applyMuted() {
        playerComponent.mediaPlayer().audio().setMute(muted);
    }

    private void setStatus(String status) {
        SwingUtilities.invokeLater(() -> UiTheme.styleStatusLabel(statusLabel, status));
    }
}
