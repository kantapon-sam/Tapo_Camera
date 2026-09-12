package local.tapo.viewer;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigLoader {
    private static final Pattern CAMERA_KEY = Pattern.compile("^camera\\.(\\d+)\\..+$");
    private static final Pattern RTSP_URL_KEY = Pattern.compile("^rtsp\\.url\\.(\\d+)$");

    private ConfigLoader() {
    }

    public static AppConfig load(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            throw new IOException("Config file not found: " + configPath.toAbsolutePath());
        }

        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            props.load(reader);
        }

        Path cameraNamesPath = cameraNamesPath(configPath);
        Properties cameraNames = loadCameraNames(cameraNamesPath);
        List<CameraConfig> cameras = loadCameras(props, cameraNames);
        CameraConfig.assignUniqueRecordingNames(cameras);

        Path recordingsDir = resolvePath(value(props, "recordings.dir", "recordings"));
        Files.createDirectories(recordingsDir);
        int segmentSeconds = intValue(props, "recording.segmentSeconds", 300, 10, 86400);
        int gridColumns = intValue(props, "grid.columns", 2, 1, 8);
        boolean liveAutoStart = booleanValue(props, "live.autoStart", true);
        String ffmpegPath = value(props, "ffmpeg.path", "ffmpeg");
        String[] vlcOptions = vlcOptions(props);

        return new AppConfig(
            configPath,
            cameras,
            cameraNamesPath,
            recordingsDir,
            ffmpegPath,
            segmentSeconds,
            gridColumns,
            liveAutoStart,
            vlcOptions
        );
    }

    public static CameraConfig addCamera(Path configPath, String rtspUrl, String name) throws IOException {
        URI endpoint = RtspUrlBuilder.validate(rtspUrl);
        Path path = configPath.toAbsolutePath().normalize();
        String original = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        Properties props = new Properties();
        props.load(new StringReader(original));
        List<CameraConfig> existing = loadCameras(props, loadCameraNames(cameraNamesPath(path)));
        Set<String> usedIds = new HashSet<>();
        for (CameraConfig camera : existing) {
            usedIds.add(camera.id());
            if (sameEndpoint(endpoint, camera.liveUrl())) {
                throw new IllegalArgumentException("This camera and stream are already in the list.");
            }
        }

        int index = 1;
        for (String key : props.stringPropertyNames()) {
            Matcher matcher = CAMERA_KEY.matcher(key);
            if (matcher.matches()) {
                index = Math.max(index, Math.addExact(Integer.parseInt(matcher.group(1)), 1));
            }
        }
        CameraConfig camera = cameraFromRtspUrl(rtspUrl, index, usedIds);
        if (name != null && !name.trim().isEmpty()) {
            camera.setName(name.trim());
        }
        existing.add(camera);
        CameraConfig.assignUniqueRecordingNames(existing);

        String prefix = "camera." + index + ".";
        Properties added = new Properties();
        added.setProperty(prefix + "enabled", "true");
        added.setProperty(prefix + "id", camera.id());
        added.setProperty(prefix + "name", camera.displayName());
        added.setProperty(prefix + "rtsp", rtspUrl);
        StringWriter block = new StringWriter();
        added.store(block, "Added from Tapo RTSP Viewer");

        // Replace only after a complete write, keeping existing comments and settings intact.
        Path temporary = Files.createTempFile(path.getParent(), "cameras-", ".tmp");
        try {
            Files.write(temporary, (original + "\n\n" + block).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return camera;
    }

    private static boolean sameEndpoint(URI endpoint, String existingUrl) {
        try {
            URI other = new URI(existingUrl);
            int port = endpoint.getPort() < 0 ? 554 : endpoint.getPort();
            int otherPort = other.getPort() < 0 ? 554 : other.getPort();
            return endpoint.getScheme().equalsIgnoreCase(other.getScheme())
                && endpoint.getHost().equalsIgnoreCase(other.getHost())
                && port == otherPort
                && java.util.Objects.equals(endpoint.getPath(), other.getPath())
                && java.util.Objects.equals(endpoint.getQuery(), other.getQuery());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static List<CameraConfig> loadCameras(Properties props, Properties cameraNames) throws IOException {
        List<CameraConfig> cameras = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();

        List<String> rtspUrls = loadRtspUrls(props);
        for (int i = 0; i < rtspUrls.size(); i++) {
            cameras.add(applySavedName(cameraFromRtspUrl(rtspUrls.get(i), i + 1, usedIds), cameraNames));
        }

        Set<Integer> indexes = new TreeSet<>();
        for (String key : props.stringPropertyNames()) {
            Matcher matcher = CAMERA_KEY.matcher(key);
            if (matcher.matches()) {
                indexes.add(Integer.parseInt(matcher.group(1)));
            }
        }

        for (Integer index : indexes) {
            String prefix = "camera." + index + ".";
            boolean enabled = booleanValue(props, prefix + "enabled", true);
            if (!enabled) {
                continue;
            }

            String id = uniqueId(value(props, prefix + "id", "camera" + index).trim(), usedIds);
            String name = value(props, prefix + "name", id).trim();
            String rtsp = value(props, prefix + "rtsp", "").trim();
            String host = value(props, prefix + "host", "").trim();
            int port = intValue(props, prefix + "port", 554, 1, 65535);
            String username = value(props, prefix + "username", "").trim();
            String password = value(props, prefix + "password", "");
            String stream = value(props, prefix + "stream", "stream1").trim();

            cameras.add(applySavedName(
                new CameraConfig(id, name, enabled, host, port, username, password, stream, rtsp),
                cameraNames
            ));
        }
        return cameras;
    }

    private static Path cameraNamesPath(Path configPath) {
        Path parent = configPath.toAbsolutePath().getParent();
        if (parent == null) {
            return resolvePath(Paths.get("config", "camera-names.properties").toString());
        }
        return parent.resolve("camera-names.properties").normalize();
    }

    private static Properties loadCameraNames(Path path) throws IOException {
        Properties names = new Properties();
        if (!Files.exists(path)) {
            return names;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            names.load(reader);
        }
        return names;
    }

    private static CameraConfig applySavedName(CameraConfig camera, Properties cameraNames) {
        String savedName = cameraNames.getProperty(CameraNameStore.propertyKey(camera.id()));
        if (savedName != null && !savedName.trim().isEmpty()) {
            camera.setName(savedName.trim());
        }
        return camera;
    }

    private static List<String> loadRtspUrls(Properties props) throws IOException {
        List<String> urls = new ArrayList<>();

        addRtspUrlsFromText(value(props, "rtsp.urls", ""), urls);

        Set<Integer> indexes = new TreeSet<>();
        for (String key : props.stringPropertyNames()) {
            Matcher matcher = RTSP_URL_KEY.matcher(key);
            if (matcher.matches()) {
                indexes.add(Integer.parseInt(matcher.group(1)));
            }
        }
        for (Integer index : indexes) {
            addRtspUrl(value(props, "rtsp.url." + index, ""), urls);
        }

        String urlsFile = value(props, "rtsp.urls.file", "").trim();
        if (!urlsFile.isEmpty()) {
            Path path = resolvePath(urlsFile);
            if (!Files.exists(path)) {
                throw new IOException("RTSP URL file not found: " + path.toAbsolutePath());
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    addRtspUrl(trimmed, urls);
                }
            }
        }

        return urls;
    }

    private static CameraConfig cameraFromRtspUrl(String rtspUrl, int index, Set<String> usedIds) {
        String name = rtspDisplayName(rtspUrl, index);
        String id = uniqueId(name, usedIds);
        return new CameraConfig(id, name, true, "", 554, "", "", "stream1", rtspUrl);
    }

    private static String rtspDisplayName(String rtspUrl, int index) {
        try {
            URI uri = new URI(rtspUrl);
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) {
                return "RTSP " + index;
            }
            String path = uri.getPath();
            if (path == null || path.trim().isEmpty() || "/".equals(path.trim())) {
                return host;
            }
            return host + path;
        } catch (URISyntaxException e) {
            return "RTSP " + index;
        }
    }

    private static void addRtspUrlsFromText(String text, List<String> urls) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        String[] parts = text.split("[\\s,;]+");
        for (String part : parts) {
            addRtspUrl(part, urls);
        }
    }

    private static void addRtspUrl(String raw, List<String> urls) {
        String url = raw == null ? "" : raw.trim();
        if (url.isEmpty()) {
            return;
        }
        String lower = url.toLowerCase();
        if (!lower.startsWith("rtsp://") && !lower.startsWith("rtsps://")) {
            throw new IllegalArgumentException("RTSP URL must start with rtsp:// or rtsps://: " + url);
        }
        urls.add(url);
    }

    private static String uniqueId(String value, Set<String> usedIds) {
        String base = value == null ? "" : value.trim();
        base = base.replaceAll("[^A-Za-z0-9._-]+", "_");
        base = base.replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "camera";
        }

        String candidate = base;
        int suffix = 2;
        while (usedIds.contains(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        usedIds.add(candidate);
        return candidate;
    }

    private static String[] vlcOptions(Properties props) {
        List<String> options = new ArrayList<>();
        String rtspTransport = value(props, "vlc.rtspTransport", "tcp").trim();
        if ("tcp".equalsIgnoreCase(rtspTransport)) {
            options.add(":rtsp-tcp");
        }
        options.add(":network-caching=" + intValue(props, "vlc.networkCachingMs", 300, 0, 10000));
        options.add(":live-caching=" + intValue(props, "vlc.liveCachingMs", 300, 0, 10000));
        return options.toArray(new String[0]);
    }

    private static Path resolvePath(String value) {
        Path path = Paths.get(value);
        if (path.isAbsolute()) {
            return path;
        }
        return Paths.get("").toAbsolutePath().resolve(path).normalize();
    }

    private static String value(Properties props, String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    private static boolean booleanValue(Properties props, String key, boolean defaultValue) {
        return Boolean.parseBoolean(props.getProperty(key, Boolean.toString(defaultValue)));
    }

    private static int intValue(Properties props, String key, int defaultValue, int min, int max) {
        String raw = props.getProperty(key);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        int value = Integer.parseInt(raw.trim());
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        }
        return value;
    }
}
