package local.tapo.viewer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.util.concurrent.ExecutionException;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

public final class PlaybackPanel extends JPanel {
    private final AppConfig config;
    private final ClipIndex clipIndex;
    private final EmbeddedMediaPlayerComponent playerComponent;
    private final JSpinner dateSpinner;
    private final DefaultListModel<ClipEntry> clipListModel;
    private final JList<ClipEntry> clipList;
    private final JLabel statusLabel;
    private Path currentClipPath;

    public PlaybackPanel(AppConfig config) {
        super(new BorderLayout(8, 8));
        this.config = config;
        this.clipIndex = new ClipIndex(config.recordingsDir());
        this.playerComponent = new EmbeddedMediaPlayerComponent();
        this.dateSpinner = new JSpinner(new SpinnerDateModel());
        this.clipListModel = new DefaultListModel<>();
        this.clipList = new JList<>(clipListModel);
        this.statusLabel = new JLabel("Stopped");

        buildUi();
        refreshClips();
    }

    private void buildUi() {
        setBackground(UiTheme.APP_BACKGROUND);
        dateSpinner.setEditor(new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd"));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        UiTheme.styleControlStrip(top);
        JButton refreshButton = new JButton("Refresh");
        JButton previousDayButton = new JButton("<");
        JButton todayButton = new JButton("Today");
        JButton nextDayButton = new JButton(">");
        JButton playButton = new JButton("Play");
        JButton stopButton = new JButton("Stop");
        JButton deleteButton = new JButton("Delete");
        JButton openFileButton = new JButton("Open File");
        JButton openUrlButton = new JButton("Open URL");
        UiTheme.styleButton(refreshButton, UiTheme.ButtonKind.SECONDARY);
        UiTheme.styleButton(previousDayButton, UiTheme.ButtonKind.SECONDARY);
        UiTheme.styleButton(todayButton, UiTheme.ButtonKind.SECONDARY);
        UiTheme.styleButton(nextDayButton, UiTheme.ButtonKind.SECONDARY);
        UiTheme.styleButton(playButton, UiTheme.ButtonKind.SUCCESS);
        UiTheme.styleButton(stopButton, UiTheme.ButtonKind.DANGER);
        UiTheme.styleButton(deleteButton, UiTheme.ButtonKind.DANGER);
        UiTheme.styleButton(openFileButton, UiTheme.ButtonKind.SECONDARY);
        UiTheme.styleButton(openUrlButton, UiTheme.ButtonKind.SECONDARY);
        UiTheme.styleStatusLabel(statusLabel, "Stopped");

        JLabel dateLabel = new JLabel("Date");
        UiTheme.stylePlainLabel(dateLabel);

        top.add(dateLabel);
        top.add(previousDayButton);
        top.add(dateSpinner);
        top.add(nextDayButton);
        top.add(todayButton);
        top.add(refreshButton);
        top.add(playButton);
        top.add(stopButton);
        top.add(deleteButton);
        top.add(openFileButton);
        top.add(openUrlButton);
        top.add(statusLabel);

        clipList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        clipList.setCellRenderer(new ClipCellRenderer());
        clipList.setBackground(UiTheme.SURFACE);
        clipList.setForeground(UiTheme.TEXT);
        clipList.setSelectionBackground(UiTheme.ACCENT);
        clipList.setSelectionForeground(java.awt.Color.WHITE);
        JScrollPane clipScroll = new JScrollPane(clipList);
        clipScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel clipPanel = new JPanel(new BorderLayout());
        UiTheme.styleSurface(clipPanel);
        JPanel clipHeader = new JPanel(new BorderLayout());
        UiTheme.styleHeader(clipHeader);
        clipHeader.add(UiTheme.cameraTitle("Recorded clips - all cameras"), BorderLayout.WEST);
        clipPanel.add(clipHeader, BorderLayout.NORTH);
        clipPanel.add(clipScroll, BorderLayout.CENTER);

        playerComponent.setBackground(UiTheme.VIDEO_BACKGROUND);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, clipPanel, playerComponent);
        splitPane.setResizeWeight(0.28);
        splitPane.setDividerSize(8);
        splitPane.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        splitPane.setBackground(UiTheme.APP_BACKGROUND);

        add(top, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        refreshButton.addActionListener(event -> refreshClips());
        previousDayButton.addActionListener(event -> moveDate(-1));
        todayButton.addActionListener(event -> setDate(LocalDate.now()));
        nextDayButton.addActionListener(event -> moveDate(1));
        playButton.addActionListener(event -> playSelectedClip());
        stopButton.addActionListener(event -> stop());
        deleteButton.addActionListener(event -> deleteSelectedClip());
        openFileButton.addActionListener(event -> openFile());
        openUrlButton.addActionListener(event -> openUrl());
        dateSpinner.addChangeListener(event -> refreshClips());
        clipList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && clipList.getSelectedValue() != null) {
                playSelectedClip();
            }
        });

        playerComponent.mediaPlayer().events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void playing(MediaPlayer mediaPlayer) {
                setStatus("Playing");
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

    public void refreshClips() {
        clipListModel.clear();

        LocalDate date = spinnerDate();
        try {
            List<ClipEntry> clips = new ArrayList<>();
            for (CameraConfig camera : config.cameras()) {
                for (Path clip : clipIndex.findClips(camera, date)) {
                    clips.add(new ClipEntry(camera, clip));
                }
            }
            clips.sort(Comparator
                .comparingLong(ClipEntry::modifiedMillis)
                .reversed()
                .thenComparing(entry -> entry.path().toString()));
            clips.forEach(clipListModel::addElement);
            setStatus(clips.size() + " clips");
        } catch (IOException e) {
            showError("Could not read clips", e);
        }
    }

    public void stop() {
        playerComponent.mediaPlayer().controls().stop();
    }

    public void release() {
        stop();
        playerComponent.release();
    }

    private void playSelectedClip() {
        ClipEntry entry = clipList.getSelectedValue();
        if (entry == null) {
            return;
        }
        Path path = entry.path();
        currentClipPath = path;
        playMedia(path.toAbsolutePath().toString(), new String[0]);
    }

    private void deleteSelectedClip() {
        ClipEntry entry = clipList.getSelectedValue();
        if (entry == null) {
            setStatus("Select a clip");
            return;
        }

        int result = JOptionPane.showConfirmDialog(
            this,
            "Delete this clip?\n" + entry.camera().displayName() + "\n" + entry.path().getFileName(),
            "Delete clip",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        Path path = entry.path();
        setStatus("Deleting...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (path.equals(currentClipPath)) {
                    stop();
                    Thread.sleep(300);
                    currentClipPath = null;
                }
                Files.deleteIfExists(path);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    refreshClips();
                    setStatus("Clip deleted");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("Could not delete clip", new IOException("Delete was interrupted", e));
                } catch (ExecutionException e) {
                    showError("Could not delete clip", exceptionFrom(e));
                }
            }
        }.execute();
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser(config.recordingsDir().toFile());
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            currentClipPath = file.toPath();
            playMedia(file.toPath().toAbsolutePath().toString(), new String[0]);
        }
    }

    private void openUrl() {
        JTextField field = new JTextField();
        int result = JOptionPane.showConfirmDialog(
            this,
            field,
            "Play media URL",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );
        if (result == JOptionPane.OK_OPTION && !field.getText().trim().isEmpty()) {
            currentClipPath = null;
            playMedia(field.getText().trim(), config.vlcOptions());
        }
    }

    private void playMedia(String media, String[] options) {
        setStatus("Opening...");
        playerComponent.mediaPlayer().media().play(media, options);
    }

    private LocalDate spinnerDate() {
        Date date = (Date) dateSpinner.getValue();
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private void moveDate(int days) {
        setDate(spinnerDate().plusDays(days));
    }

    private void setDate(LocalDate date) {
        Date newDate = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        dateSpinner.setValue(newDate);
    }

    private Exception exceptionFrom(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof Exception) {
            return (Exception) cause;
        }
        return new IOException(cause == null ? e.getMessage() : cause.getMessage(), cause);
    }

    private void setStatus(String status) {
        SwingUtilities.invokeLater(() -> UiTheme.styleStatusLabel(statusLabel, status));
    }

    private void showError(String message, Exception e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(this, message + ": " + e.getMessage(), "Playback error", JOptionPane.ERROR_MESSAGE);
    }

    private static final class ClipEntry {
        private final CameraConfig camera;
        private final Path path;

        private ClipEntry(CameraConfig camera, Path path) {
            this.camera = camera;
            this.path = path;
        }

        private CameraConfig camera() {
            return camera;
        }

        private Path path() {
            return path;
        }

        private long modifiedMillis() {
            try {
                return Files.getLastModifiedTime(path).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }

        @Override
        public String toString() {
            return camera.displayName() + " - " + path.getFileName();
        }
    }

    private static final class ClipCellRenderer extends DefaultListCellRenderer {
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        @Override
        public Component getListCellRendererComponent(
            JList<?> list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus
        ) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ClipEntry) {
                ClipEntry entry = (ClipEntry) value;
                Path path = entry.path();
                String details = entry.camera().displayName() + "  |  " + path.getFileName().toString();
                try {
                    long bytes = Files.size(path);
                    Date modified = new Date(Files.getLastModifiedTime(path).toMillis());
                    details += "  |  " + dateFormat.format(modified) + "  |  " + humanBytes(bytes);
                } catch (IOException ignored) {
                    // Keep the filename if file metadata cannot be read.
                }
                setText(details);
            }
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            return this;
        }

        private static String humanBytes(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            }
            if (bytes < 1024 * 1024) {
                return bytes / 1024 + " KB";
            }
            return bytes / (1024 * 1024) + " MB";
        }
    }
}
