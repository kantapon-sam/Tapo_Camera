package local.tapo.viewer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

public final class CameraNameStore {
    private CameraNameStore() {
    }

    public static String propertyKey(String cameraId) {
        return "camera." + cameraId + ".name";
    }

    public static void save(Path path, List<CameraConfig> cameras) throws IOException {
        Properties props = new Properties();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
        } else if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        for (CameraConfig camera : cameras) {
            props.setProperty(propertyKey(camera.id()), camera.displayName());
        }

        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            props.store(writer, "Tapo RTSP Viewer camera display names");
        }
    }
}
