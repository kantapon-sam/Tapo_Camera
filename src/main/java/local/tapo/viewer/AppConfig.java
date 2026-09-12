package local.tapo.viewer;

import java.nio.file.Path;
import java.util.List;

public final class AppConfig {
    private final Path configPath;
    private final List<CameraConfig> cameras;
    private final Path cameraNamesPath;
    private final Path recordingsDir;
    private final String ffmpegPath;
    private final int recordingSegmentSeconds;
    private final int gridColumns;
    private final boolean liveAutoStart;
    private final String[] vlcOptions;

    public AppConfig(
        Path configPath,
        List<CameraConfig> cameras,
        Path cameraNamesPath,
        Path recordingsDir,
        String ffmpegPath,
        int recordingSegmentSeconds,
        int gridColumns,
        boolean liveAutoStart,
        String[] vlcOptions
    ) {
        this.configPath = configPath.toAbsolutePath().normalize();
        this.cameras = cameras;
        this.cameraNamesPath = cameraNamesPath;
        this.recordingsDir = recordingsDir;
        this.ffmpegPath = ffmpegPath;
        this.recordingSegmentSeconds = recordingSegmentSeconds;
        this.gridColumns = gridColumns;
        this.liveAutoStart = liveAutoStart;
        this.vlcOptions = vlcOptions.clone();
    }

    public List<CameraConfig> cameras() {
        return cameras;
    }

    public Path configPath() {
        return configPath;
    }

    public Path cameraNamesPath() {
        return cameraNamesPath;
    }

    public Path recordingsDir() {
        return recordingsDir;
    }

    public String ffmpegPath() {
        return ffmpegPath;
    }

    public int recordingSegmentSeconds() {
        return recordingSegmentSeconds;
    }

    public int gridColumns() {
        return gridColumns;
    }

    public boolean liveAutoStart() {
        return liveAutoStart;
    }

    public String[] vlcOptions() {
        return vlcOptions.clone();
    }
}
