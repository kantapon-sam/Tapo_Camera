package local.tapo.viewer;

public final class CameraConfig {
    private final String id;
    private String name;
    private String recordingName;
    private final boolean enabled;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String stream;
    private final String rtspUrl;

    public CameraConfig(
        String id,
        String name,
        boolean enabled,
        String host,
        int port,
        String username,
        String password,
        String stream,
        String rtspUrl
    ) {
        this.id = id;
        this.name = name;
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.stream = stream;
        this.rtspUrl = rtspUrl;
        this.recordingName = safeFileName(displayName());
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean enabled() {
        return enabled;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String stream() {
        return stream;
    }

    public String rtspUrl() {
        return rtspUrl;
    }

    public String displayName() {
        return name == null || name.trim().isEmpty() ? id : name;
    }

    public String recordingName() {
        return recordingName;
    }

    public void setRecordingName(String recordingName) {
        this.recordingName = safeFileName(recordingName);
    }

    public String legacyRecordingName() {
        return id;
    }

    public String liveUrl() {
        if (rtspUrl != null && !rtspUrl.trim().isEmpty()) {
            return rtspUrl;
        }
        return RtspUrlBuilder.build(username, password, host, port, stream);
    }

    public static String safeFileName(String value) {
        String name = value == null ? "" : value.trim();
        name = name.replaceAll("[\\\\/:*?\"<>|]+", "_");
        name = name.replaceAll("\\s+", " ");
        name = name.replaceAll("^\\.+|\\.+$", "");
        if (name.isEmpty()) {
            return "camera";
        }
        return name;
    }

    public static void assignUniqueRecordingNames(Iterable<CameraConfig> cameras) {
        java.util.Set<String> used = new java.util.HashSet<>();
        for (CameraConfig camera : cameras) {
            String base = safeFileName(camera.displayName());
            String candidate = base;
            int suffix = 2;
            while (used.contains(candidate.toLowerCase(java.util.Locale.ROOT))) {
                candidate = base + "_" + suffix;
                suffix++;
            }
            used.add(candidate.toLowerCase(java.util.Locale.ROOT));
            camera.setRecordingName(candidate);
        }
    }

    @Override
    public String toString() {
        return displayName();
    }
}
