package local.tapo.viewer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class FfmpegRecorder {
    private static final int STARTUP_CHECK_MILLIS = 1200;
    private static final int MAX_LOG_CHARS = 4000;

    private final CameraConfig camera;
    private final AppConfig config;
    private final StringBuilder recentOutput = new StringBuilder();
    private Process process;

    public FfmpegRecorder(CameraConfig camera, AppConfig config) {
        this.camera = camera;
        this.config = config;
    }

    public synchronized void start() throws IOException {
        if (isRunning()) {
            return;
        }
        clearRecentOutput();

        Path outputDir = config.recordingsDir().resolve(camera.recordingName());
        Files.createDirectories(outputDir);
        Path outputPattern = outputDir.resolve(camera.recordingName() + "_%Y%m%d_%H%M%S.mkv");

        List<String> command = new ArrayList<>();
        command.add(resolveFfmpegPath());
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("warning");
        command.add("-rtsp_transport");
        command.add("tcp");
        command.add("-i");
        command.add(camera.liveUrl());
        command.add("-map");
        command.add("0");
        command.add("-c");
        command.add("copy");
        command.add("-f");
        command.add("segment");
        command.add("-segment_time");
        command.add(Integer.toString(config.recordingSegmentSeconds()));
        command.add("-segment_format");
        command.add("matroska");
        command.add("-reset_timestamps");
        command.add("1");
        command.add("-strftime");
        command.add("1");
        command.add(outputPattern.toString());

        try {
            process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        } catch (IOException e) {
            throw new IOException(
                "Could not start ffmpeg for " + camera.displayName()
                    + ". Set ffmpeg.path in config/cameras.properties or put ffmpeg.exe in tools/ffmpeg/bin.",
                e
            );
        }

        startLogDrainer(process);

        try {
            if (process.waitFor(STARTUP_CHECK_MILLIS, TimeUnit.MILLISECONDS)) {
                int exitCode = process.exitValue();
                process = null;
                throw new IOException(
                    "ffmpeg stopped immediately for " + camera.displayName()
                        + " (exit " + exitCode + ")." + recentOutputText()
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Interrupted while starting ffmpeg for " + camera.displayName(), e);
        }
    }

    public synchronized void stop() {
        if (!isRunning()) {
            return;
        }

        try {
            OutputStream stdin = process.getOutputStream();
            stdin.write("q\n".getBytes(StandardCharsets.US_ASCII));
            stdin.flush();
        } catch (IOException ignored) {
            // Fall through to destroy if ffmpeg is already gone or stdin is closed.
        }

        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        } finally {
            process = null;
        }
    }

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    private String resolveFfmpegPath() throws IOException {
        String configured = config.ffmpegPath() == null ? "" : config.ffmpegPath().trim();
        if (configured.isEmpty()) {
            configured = "ffmpeg";
        }

        Path configuredPath = Paths.get(configured);
        if (configuredPath.isAbsolute() || hasDirectoryPart(configured)) {
            Path resolved = configuredPath.isAbsolute()
                ? configuredPath
                : Paths.get("").toAbsolutePath().resolve(configuredPath).normalize();
            if (Files.isRegularFile(resolved)) {
                return resolved.toString();
            }
            throw new IOException("ffmpeg not found: " + resolved);
        }

        List<Path> candidates = new ArrayList<>();
        candidates.add(Paths.get("tools", "ffmpeg", "bin", "ffmpeg.exe"));
        candidates.add(Paths.get(".tools", "ffmpeg", "bin", "ffmpeg.exe"));
        candidates.add(Paths.get("ffmpeg", "bin", "ffmpeg.exe"));
        candidates.add(Paths.get("bin", "ffmpeg.exe"));
        candidates.add(Paths.get("ffmpeg.exe"));

        for (Path candidate : candidates) {
            Path resolved = Paths.get("").toAbsolutePath().resolve(candidate).normalize();
            if (Files.isRegularFile(resolved)) {
                return resolved.toString();
            }
        }

        return configured;
    }

    private boolean hasDirectoryPart(String value) {
        return value.contains("/") || value.contains("\\");
    }

    private String recentOutputText() {
        synchronized (recentOutput) {
            if (recentOutput.length() == 0) {
                return "";
            }
            return System.lineSeparator() + recentOutput.toString().trim();
        }
    }

    private void clearRecentOutput() {
        synchronized (recentOutput) {
            recentOutput.setLength(0);
        }
    }

    private void startLogDrainer(Process startedProcess) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(startedProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    appendRecentOutput(line);
                    System.out.println("[ffmpeg " + camera.id() + "] " + line);
                }
            } catch (IOException ignored) {
                // The process stream closes during normal shutdown.
            }
        }, "ffmpeg-log-" + camera.id());
        thread.setDaemon(true);
        thread.start();
    }

    private void appendRecentOutput(String line) {
        synchronized (recentOutput) {
            recentOutput.append(line).append(System.lineSeparator());
            if (recentOutput.length() > MAX_LOG_CHARS) {
                recentOutput.delete(0, recentOutput.length() - MAX_LOG_CHARS);
            }
        }
    }
}
