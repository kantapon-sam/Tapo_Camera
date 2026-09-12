package local.tapo.viewer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

public final class LiveCameraPanel extends JPanel {
    private final CameraConfig camera;
    private final String[] vlcOptions;
    private final Consumer<LiveCameraPanel> largeViewOpener;
    private final Consumer<LiveCameraPanel> renameHandler;
    private final EmbeddedMediaPlayerComponent playerComponent;
    private final JLabel titleLabel;
    private final JLabel statusLabel;
    private final JButton muteButton;
    private final JButton deleteButton;
    private boolean released;
    private boolean liveRequested;
    private boolean muted;

    public LiveCameraPanel(
        CameraConfig camera,
        String[] vlcOptions,
        Consumer<LiveCameraPanel> largeViewOpener,
        Consumer<LiveCameraPanel> renameHandler,
        Consumer<LiveCameraPanel> deleteHandler
    ) {
        super(new BorderLayout(0, 0));
        this.camera = camera;
        this.vlcOptions = vlcOptions.clone();
        this.largeViewOpener = largeViewOpener;
        this.renameHandler = renameHandler;
        this.playerComponent = new EmbeddedMediaPlayerComponent();
        this.titleLabel = UiTheme.cameraTitle(camera.displayName());
        this.statusLabel = new JLabel("Stopped");
        this.muteButton = new JButton("Mute");
        this.deleteButton = new JButton("Delete");

        UiTheme.styleSurface(this);
        setPreferredSize(new Dimension(520, 360));

        JPanel header = new JPanel(new BorderLayout());
        UiTheme.styleHeader(header);
        header.add(titleLabel, BorderLayout.WEST);
        UiTheme.styleStatusLabel(statusLabel, "Stopped");
        header.add(statusLabel, BorderLayout.EAST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        UiTheme.styleControlStrip(controls);
        JButton playButton = new JButton("Live");
        JButton stopButton = new JButton("Stop");
        JButton largeButton = new JButton("Large");
        JButton renameButton = new JButton("Rename");
        JButton accountButton = new JButton("User / Pass");
        accountButton.setToolTipText("View this camera's username and password");
        deleteButton.setToolTipText("Remove this camera and keep recorded clips");
        UiTheme.styleButton(playButton, UiTheme.ButtonKind.SUCCESS);
        UiTheme.styleButton(stopButton, UiTheme.ButtonKind.DANGER);
        UiTheme.styleButton(muteButton, UiTheme.ButtonKind.SECONDARY);
        UiTheme.styleButton(largeButton, UiTheme.ButtonKind.PRIMARY);
        UiTheme.styleButton(renameButton, UiTheme.ButtonKind.SECONDARY);
        UiTheme.styleButton(accountButton, UiTheme.ButtonKind.SECONDARY);
        UiTheme.styleButton(deleteButton, UiTheme.ButtonKind.DANGER);
        controls.add(playButton);
        controls.add(stopButton);
        controls.add(muteButton);
        controls.add(largeButton);
        controls.add(renameButton);
        controls.add(accountButton);
        controls.add(deleteButton);

        playerComponent.setBackground(UiTheme.VIDEO_BACKGROUND);
        add(header, BorderLayout.NORTH);
        add(playerComponent, BorderLayout.CENTER);
        add(controls, BorderLayout.SOUTH);

        playButton.addActionListener(event -> play());
        stopButton.addActionListener(event -> stop());
        muteButton.addActionListener(event -> setMuted(!muted));
        largeButton.addActionListener(event -> openLargeView());
        renameButton.addActionListener(event -> rename());
        accountButton.addActionListener(event -> CameraAccountDialog.show(this, camera));
        deleteButton.addActionListener(event -> deleteHandler.accept(this));

        playerComponent.mediaPlayer().events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void playing(MediaPlayer mediaPlayer) {
                applyMuted();
                setStatus("Live");
            }

            @Override
            public void stopped(MediaPlayer mediaPlayer) {
                liveRequested = false;
                setStatus("Stopped");
            }

            @Override
            public void error(MediaPlayer mediaPlayer) {
                liveRequested = false;
                setStatus("Error");
            }
        });
    }

    public void play() {
        if (released) {
            return;
        }
        liveRequested = true;
        setStatus("Connecting...");
        playerComponent.mediaPlayer().media().play(camera.liveUrl(), vlcOptions);
        applyMuted();
    }

    public void stop() {
        liveRequested = false;
        playerComponent.mediaPlayer().controls().stop();
    }

    public boolean isLiveRequested() {
        return liveRequested;
    }

    public CameraConfig camera() {
        return camera;
    }

    public void refreshCameraName() {
        SwingUtilities.invokeLater(() -> titleLabel.setText(camera.displayName()));
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        applyMuted();
        updateMuteButton();
    }

    public void release() {
        if (released) {
            return;
        }
        stop();
        released = true;
        playerComponent.release();
    }

    public void setCameraRemovalEnabled(boolean enabled) {
        deleteButton.setEnabled(enabled);
        deleteButton.setToolTipText(enabled ? "Remove this camera and keep recorded clips"
            : "Stop recording before deleting a camera");
    }

    private void openLargeView() {
        if (largeViewOpener != null) {
            largeViewOpener.accept(this);
        }
    }

    private void rename() {
        if (renameHandler != null) {
            renameHandler.accept(this);
        }
    }

    private void applyMuted() {
        playerComponent.mediaPlayer().audio().setMute(muted);
    }

    private void updateMuteButton() {
        SwingUtilities.invokeLater(() -> {
            muteButton.setText(muted ? "Unmute" : "Mute");
            UiTheme.styleButton(muteButton, muted ? UiTheme.ButtonKind.WARNING : UiTheme.ButtonKind.SECONDARY);
        });
    }

    private void setStatus(String status) {
        SwingUtilities.invokeLater(() -> UiTheme.styleStatusLabel(statusLabel, status));
    }
}
