package local.tapo.viewer;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RecorderManager {
    private final Map<String, FfmpegRecorder> recorders = new LinkedHashMap<>();
    private final AppConfig config;

    public RecorderManager(AppConfig config) {
        this.config = config;
        for (CameraConfig camera : config.cameras()) {
            addCamera(camera);
        }
    }

    public void addCamera(CameraConfig camera) {
        recorders.putIfAbsent(camera.id(), new FfmpegRecorder(camera, config));
    }

    public void removeCamera(CameraConfig camera) {
        FfmpegRecorder recorder = recorders.get(camera.id());
        if (recorder != null) {
            if (recorder.isRunning()) {
                throw new IllegalStateException("Stop recording before deleting a camera.");
            }
            recorders.remove(camera.id());
        }
    }

    public void startAll() throws IOException {
        StringBuilder failures = new StringBuilder();
        for (FfmpegRecorder recorder : recorders.values()) {
            try {
                recorder.start();
            } catch (IOException e) {
                if (failures.length() > 0) {
                    failures.append(System.lineSeparator());
                }
                failures.append(e.getMessage());
            }
        }
        if (failures.length() > 0) {
            stopAll();
            throw new IOException(failures.toString());
        }
    }

    public void stopAll() {
        recorders.values().forEach(FfmpegRecorder::stop);
    }

    public boolean isAnyRunning() {
        return recorders.values().stream().anyMatch(FfmpegRecorder::isRunning);
    }
}
