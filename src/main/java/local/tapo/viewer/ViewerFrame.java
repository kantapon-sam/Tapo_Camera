package local.tapo.viewer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.util.concurrent.ExecutionException;

public final class ViewerFrame extends JFrame {
    private final AppConfig config;
    private final RecorderManager recorderManager;
    private final List<LiveCameraPanel> livePanels = new ArrayList<>();
    private final List<LargeCameraFrame> largeFrames = new ArrayList<>();
    private final List<LiveCameraPanel> livePanelsPausedForRecording = new ArrayList<>();
    private final PlaybackPanel playbackPanel;
    private final JLabel statusLabel = new JLabel("Ready");
    private JPanel liveGrid;
    private JTabbedPane tabs;
    private JButton addCameraButton;
    private JButton muteAllButton;
    private boolean recordingStartPending;
    private boolean recordingStopPending;
    private boolean allMuted = true;
    private boolean shuttingDown;

    public ViewerFrame(AppConfig config, boolean vlcFound) {
        super("Tapo RTSP Viewer");
        this.config = config;
        this.recorderManager = new RecorderManager(config);
        this.playbackPanel = new PlaybackPanel(config);

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1280, 820);
        setLayout(new BorderLayout());
        getContentPane().setBackground(UiTheme.APP_BACKGROUND);

        add(buildToolbar(vlcFound), BorderLayout.NORTH);
        add(buildTabs(), BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                shutdown();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }

    public void playAll() {
        livePanels.forEach(LiveCameraPanel::play);
    }

    private JToolBar buildToolbar(boolean vlcFound) {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        addCameraButton = new JButton("+ Add Camera");
        addCameraButton.setToolTipText("Add camera");
        UiTheme.styleButton(addCameraButton, UiTheme.ButtonKind.PRIMARY);
        addCameraButton.addActionListener(event -> addCamera());
        JButton playAllButton = new JButton("Live All");
        JButton stopAllButton = new JButton("Stop All");
        muteAllButton = new JButton(allMuted ? "Unmute All" : "Mute All");
        JButton startRecordingButton = new JButton("Record All");
        JButton stopRecordingButton = new JButton("Stop Record");
        JButton refreshPlaybackButton = new JButton("Refresh Clips");
        UiTheme.styleButton(playAllButton, UiTheme.ButtonKind.SUCCESS);
        UiTheme.styleButton(stopAllButton, UiTheme.ButtonKind.DANGER);
        UiTheme.styleButton(muteAllButton, allMuted ? UiTheme.ButtonKind.WARNING : UiTheme.ButtonKind.SECONDARY);
        UiTheme.styleButton(startRecordingButton, UiTheme.ButtonKind.PRIMARY);
        UiTheme.styleButton(stopRecordingButton, UiTheme.ButtonKind.SECONDARY);
        UiTheme.styleButton(refreshPlaybackButton, UiTheme.ButtonKind.SECONDARY);
        UiTheme.styleStatusLabel(statusLabel, "Ready");

        playAllButton.addActionListener(event -> playAll());
        stopAllButton.addActionListener(event -> livePanels.forEach(LiveCameraPanel::stop));
        muteAllButton.addActionListener(event -> setAllMuted(!allMuted));
        startRecordingButton.addActionListener(event -> startRecording());
        stopRecordingButton.addActionListener(event -> stopRecording());
        refreshPlaybackButton.addActionListener(event -> playbackPanel.refreshClips());

        toolbar.setBackground(UiTheme.SURFACE);
        toolbar.setBorder(javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER),
            javax.swing.BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        toolbar.add(UiTheme.toolbarTitle("Tapo RTSP Viewer"));
        toolbar.addSeparator(new Dimension(18, 0));
        toolbar.add(addCameraButton);
        toolbar.addSeparator();
        toolbar.add(playAllButton);
        toolbar.add(stopAllButton);
        toolbar.add(muteAllButton);
        toolbar.addSeparator();
        toolbar.add(startRecordingButton);
        toolbar.add(stopRecordingButton);
        toolbar.addSeparator();
        toolbar.add(refreshPlaybackButton);
        toolbar.addSeparator();
        toolbar.add(statusLabel);
        toolbar.setToolTipText("Config: " + config.configPath());

        if (!vlcFound) {
            setStatus("VLC not found. Install 64-bit VLC before playing streams.");
        }

        return toolbar;
    }

    private JTabbedPane buildTabs() {
        tabs = new JTabbedPane();
        tabs.setBackground(UiTheme.APP_BACKGROUND);
        tabs.setForeground(UiTheme.TEXT);
        tabs.addTab("Live", buildLiveGrid());
        tabs.addTab("Playback", playbackPanel);
        return tabs;
    }

    private JScrollPane buildLiveGrid() {
        liveGrid = new JPanel(new GridLayout(0, config.gridColumns(), 12, 12));
        liveGrid.setBackground(UiTheme.APP_BACKGROUND);
        liveGrid.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
        for (CameraConfig camera : config.cameras()) {
            addLivePanel(camera);
        }
        JScrollPane scrollPane = new JScrollPane(liveGrid);
        scrollPane.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(UiTheme.APP_BACKGROUND);
        return scrollPane;
    }

    private LiveCameraPanel addLivePanel(CameraConfig camera) {
        LiveCameraPanel panel = new LiveCameraPanel(camera, config.vlcOptions(),
            this::openLargeCamera, this::renameCamera, this::deleteCamera);
        panel.setMuted(allMuted);
        livePanels.add(panel);
        liveGrid.add(panel);
        return panel;
    }

    private void addCamera() {
        if (recordingStartPending || recordingStopPending || recorderManager.isAnyRunning()) {
            setStatus("Stop recording before adding a camera");
            return;
        }
        CameraConfig camera = AddCameraDialog.show(this, config);
        if (camera == null || shuttingDown) {
            return;
        }
        config.cameras().add(camera);
        CameraConfig.assignUniqueRecordingNames(config.cameras());
        recorderManager.addCamera(camera);
        LiveCameraPanel panel = addLivePanel(camera);
        liveGrid.revalidate();
        liveGrid.repaint();
        tabs.setSelectedIndex(0);
        playbackPanel.refreshClips();
        setStatus("Camera added");
        SwingUtilities.invokeLater(() -> {
            if (!shuttingDown) {
                liveGrid.scrollRectToVisible(panel.getBounds());
                if (config.liveAutoStart()) {
                    panel.play();
                }
            }
        });
    }

    private void setAllMuted(boolean muted) {
        allMuted = muted;
        livePanels.forEach(panel -> panel.setMuted(muted));
        largeFrames.forEach(frame -> frame.setMuted(muted));
        updateMuteAllButton();
        setStatus(muted ? "All muted" : "Audio enabled");
    }

    private void deleteCamera(LiveCameraPanel panel) {
        if (shuttingDown || !livePanels.contains(panel)) {
            return;
        }
        if (recordingStartPending || recordingStopPending || recorderManager.isAnyRunning()) {
            setStatus("Stop recording before deleting a camera");
            return;
        }
        CameraConfig camera = panel.camera();
        int choice = JOptionPane.showConfirmDialog(this,
            "Delete camera \"" + camera.displayName() + "\"?\nRecorded clips will be kept."
                + "\nYou can still open them with Playback > Open File.",
            "Delete Camera", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION || shuttingDown) {
            return;
        }
        try {
            ConfigLoader.removeCamera(config.configPath(), camera);
        } catch (IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this,
                "Could not delete the camera. Check file permissions or reopen the app if the camera list changed.",
                "Delete Camera", JOptionPane.ERROR_MESSAGE);
            return;
        }
        recorderManager.removeCamera(camera);
        config.cameras().remove(camera);
        livePanels.remove(panel);
        livePanelsPausedForRecording.remove(panel);
        liveGrid.remove(panel);
        new ArrayList<>(largeFrames).stream()
            .filter(frame -> frame.camera() == camera)
            .forEach(LargeCameraFrame::dispose);
        panel.release();
        liveGrid.revalidate();
        liveGrid.repaint();
        playbackPanel.refreshClips();
        setStatus("Camera deleted; recorded clips kept");
    }

    private void updateCameraManagementButtons() {
        boolean enabled = !shuttingDown && !recordingStartPending && !recordingStopPending
            && !recorderManager.isAnyRunning();
        addCameraButton.setEnabled(enabled);
        livePanels.forEach(panel -> panel.setCameraRemovalEnabled(enabled));
    }

    private void updateMuteAllButton() {
        if (muteAllButton == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            muteAllButton.setText(allMuted ? "Unmute All" : "Mute All");
            UiTheme.styleButton(muteAllButton, allMuted ? UiTheme.ButtonKind.WARNING : UiTheme.ButtonKind.SECONDARY);
        });
    }

    private void renameCamera(LiveCameraPanel sourcePanel) {
        CameraConfig camera = sourcePanel.camera();
        String oldRecordingName = camera.recordingName();
        String newName = JOptionPane.showInputDialog(
            this,
            "Camera name",
            camera.displayName()
        );
        if (newName == null) {
            return;
        }

        String trimmed = newName.trim();
        if (trimmed.isEmpty()) {
            setStatus("Camera name is empty");
            return;
        }

        camera.setName(trimmed);
        CameraConfig.assignUniqueRecordingNames(config.cameras());
        livePanels.forEach(LiveCameraPanel::refreshCameraName);
        largeFrames.forEach(LargeCameraFrame::refreshCameraName);
        try {
            moveRecordingFolder(oldRecordingName, camera.recordingName());
        } catch (IOException e) {
            showError("Could not move recording folder", e);
        }
        playbackPanel.refreshClips();
        try {
            CameraNameStore.save(config.cameraNamesPath(), config.cameras());
            setStatus("Camera renamed");
        } catch (IOException e) {
            showError("Could not save camera name", e);
        }
    }

    private void moveRecordingFolder(String oldRecordingName, String newRecordingName) throws IOException {
        if (oldRecordingName == null || newRecordingName == null || oldRecordingName.equals(newRecordingName)) {
            return;
        }

        Path oldDir = config.recordingsDir().resolve(oldRecordingName);
        Path newDir = config.recordingsDir().resolve(newRecordingName);
        if (!Files.isDirectory(oldDir)) {
            return;
        }

        if (!Files.exists(newDir)) {
            try {
                Files.move(oldDir, newDir, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(oldDir, newDir);
            }
            return;
        }

        try (java.util.stream.Stream<Path> paths = Files.list(oldDir)) {
            for (Path source : paths.toArray(Path[]::new)) {
                Files.move(source, uniqueTarget(newDir.resolve(source.getFileName())));
            }
        }
        try {
            Files.delete(oldDir);
        } catch (IOException ignored) {
            // Keep the old folder if it still contains files locked by another process.
        }
    }

    private Path uniqueTarget(Path target) {
        if (!Files.exists(target)) {
            return target;
        }

        String fileName = target.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        Path parent = target.getParent();
        int suffix = 2;
        Path candidate;
        do {
            candidate = parent.resolve(base + "_" + suffix + extension);
            suffix++;
        } while (Files.exists(candidate));
        return candidate;
    }

    private void openLargeCamera(LiveCameraPanel sourcePanel) {
        CameraConfig camera = sourcePanel.camera();
        boolean resumeSourcePanel = sourcePanel.isLiveRequested();
        if (resumeSourcePanel) {
            sourcePanel.stop();
        }

        LargeCameraFrame frame = new LargeCameraFrame(camera, config.vlcOptions(), sourcePanel.isMuted() || allMuted);
        largeFrames.add(frame);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                largeFrames.remove(frame);
                if (resumeSourcePanel && !shuttingDown && livePanels.contains(sourcePanel)) {
                    playAfterDelay(() -> {
                        if (!shuttingDown && livePanels.contains(sourcePanel) && sourcePanel.isDisplayable()) {
                            sourcePanel.play();
                        }
                    });
                }
            }
        });
        frame.setLocationRelativeTo(this);
        frame.setVisible(true);
        frame.setExtendedState(frame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
        if (resumeSourcePanel) {
            playAfterDelay(() -> {
                if (frame.isDisplayable()) {
                    frame.play();
                }
            });
        } else {
            frame.play();
        }
    }

    private void playAfterDelay(Runnable playAction) {
        runAfterDelay(700, playAction);
    }

    private void runAfterDelay(int delayMillis, Runnable action) {
        Timer timer = new Timer(delayMillis, event -> action.run());
        timer.setRepeats(false);
        timer.start();
    }

    private void startRecording() {
        if (recordingStartPending || recordingStopPending || recorderManager.isAnyRunning()) {
            setStatus("Recording is already running");
            return;
        }

        livePanelsPausedForRecording.clear();
        for (LiveCameraPanel panel : livePanels) {
            if (panel.isLiveRequested()) {
                livePanelsPausedForRecording.add(panel);
                panel.stop();
            }
        }
        new ArrayList<>(largeFrames).forEach(LargeCameraFrame::stop);

        recordingStartPending = true;
        updateCameraManagementButtons();
        setStatus("Starting recording...");
        runAfterDelay(2000, this::startRecordingWorker);
    }

    private void startRecordingWorker() {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws IOException {
                recorderManager.startAll();
                return null;
            }

            @Override
            protected void done() {
                recordingStartPending = false;
                try {
                    get();
                    setStatus("Recording to " + config.recordingsDir());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    resumeLivePanelsPausedForRecording();
                    showError("Could not start recording", new IOException("Recording start was interrupted", e));
                } catch (ExecutionException e) {
                    resumeLivePanelsPausedForRecording();
                    showError("Could not start recording", exceptionFrom(e));
                } finally {
                    updateCameraManagementButtons();
                }
            }
        }.execute();
    }

    private void stopRecording() {
        if (recordingStartPending || recordingStopPending) {
            setStatus("Please wait for the recording operation to finish");
            return;
        }
        recordingStopPending = true;
        updateCameraManagementButtons();
        setStatus("Stopping recording...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                recorderManager.stopAll();
                return null;
            }

            @Override
            protected void done() {
                recordingStopPending = false;
                updateCameraManagementButtons();
                playbackPanel.refreshClips();
                setStatus("Recording stopped");
                resumeLivePanelsPausedForRecording();
            }
        }.execute();
    }

    private void resumeLivePanelsPausedForRecording() {
        if (livePanelsPausedForRecording.isEmpty() || shuttingDown) {
            livePanelsPausedForRecording.clear();
            return;
        }

        List<LiveCameraPanel> panelsToResume = new ArrayList<>(livePanelsPausedForRecording);
        livePanelsPausedForRecording.clear();
        playAfterDelay(() -> panelsToResume.stream()
            .filter(LiveCameraPanel::isDisplayable)
            .forEach(LiveCameraPanel::play));
    }

    private Exception exceptionFrom(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof Exception) {
            return (Exception) cause;
        }
        return new IOException(cause == null ? e.getMessage() : cause.getMessage(), cause);
    }

    private void shutdown() {
        shuttingDown = true;
        livePanels.forEach(LiveCameraPanel::release);
        new ArrayList<>(largeFrames).forEach(LargeCameraFrame::dispose);
        largeFrames.clear();
        playbackPanel.release();
        if (recorderManager.isAnyRunning()) {
            recorderManager.stopAll();
        }
    }

    private void setStatus(String status) {
        SwingUtilities.invokeLater(() -> UiTheme.styleStatusLabel(statusLabel, status));
    }

    private void showError(String title, Exception e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(this, e.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }
}
