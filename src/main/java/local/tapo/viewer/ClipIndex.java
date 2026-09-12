package local.tapo.viewer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ClipIndex {
    private static final List<String> VIDEO_EXTENSIONS = Arrays.asList(".mp4", ".mkv", ".ts", ".mov", ".avi");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final Path recordingsDir;

    public ClipIndex(Path recordingsDir) {
        this.recordingsDir = recordingsDir;
    }

    public List<Path> findClips(CameraConfig camera, LocalDate date) throws IOException {
        Set<Path> cameraDirs = new LinkedHashSet<>();
        cameraDirs.add(recordingsDir.resolve(camera.recordingName()));
        cameraDirs.add(recordingsDir.resolve(camera.legacyRecordingName()));

        List<Path> clips = new ArrayList<>();
        for (Path cameraDir : cameraDirs) {
            if (!Files.isDirectory(cameraDir)) {
                continue;
            }
            clips.addAll(findClipsInDirectory(cameraDir, date));
        }
        clips.sort(Comparator.comparing(Path::toString));
        return clips;
    }

    private List<Path> findClipsInDirectory(Path cameraDir, LocalDate date) throws IOException {
        try (Stream<Path> paths = Files.walk(cameraDir)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(this::isVideoFile)
                .filter(path -> matchesDate(path, date))
                .collect(Collectors.toList());
        }
    }

    private boolean isVideoFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return VIDEO_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private boolean matchesDate(Path path, LocalDate date) {
        String yyyymmdd = date.format(FILE_DATE);
        if (path.getFileName().toString().contains(yyyymmdd)) {
            return true;
        }

        try {
            Instant modified = Files.getLastModifiedTime(path).toInstant();
            LocalDate modifiedDate = modified.atZone(ZoneId.systemDefault()).toLocalDate();
            return modifiedDate.equals(date);
        } catch (IOException e) {
            return false;
        }
    }
}
