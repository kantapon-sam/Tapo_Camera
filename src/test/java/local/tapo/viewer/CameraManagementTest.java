package local.tapo.viewer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class CameraManagementTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private Path config(String settings) throws Exception {
        Path path = temporary.getRoot().toPath().resolve("selected.properties");
        Path recordings = temporary.getRoot().toPath().resolve("recordings");
        Files.write(path, ("recordings.dir=" + recordings.toString().replace('\\', '/')
            + "\nlive.autoStart=false\n" + settings).getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private String text(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void credentialsDecodeEachPartWithoutLosingColonsPlusOrUnicode() {
        CameraConfig camera = new CameraConfig("one", "One", true, "", 554, "", "", "stream1",
            "rtsp://u%3Aser+%40:p%3Ass+%20%25%2B%E0%B8%81@192.0.2.10:554/stream1");
        CameraCredentials account = CameraCredentials.from(camera);
        assertEquals("u:ser+@", account.username());
        assertEquals("p:ss+ %+\u0e01", account.password());
    }

    @Test
    public void credentialsSupportSeparateFieldsUsernameOnlyAndAnonymousUrls() {
        CameraConfig separate = new CameraConfig("one", "One", true, "192.0.2.10", 554,
            "user:name", "p:a +%", "stream1", "");
        assertEquals("user:name", CameraCredentials.from(separate).username());
        assertEquals("p:a +%", CameraCredentials.from(separate).password());
        CameraConfig userOnly = new CameraConfig("two", "Two", true, "", 554, "", "", "stream1",
            "rtsp://user@192.0.2.10/stream1");
        assertEquals("user", CameraCredentials.from(userOnly).username());
        assertEquals("", CameraCredentials.from(userOnly).password());
        CameraConfig anonymous = new CameraConfig("three", "Three", true, "", 554, "", "", "stream1",
            "rtsps://192.0.2.10/stream1");
        assertEquals("", CameraCredentials.from(anonymous).username());
        assertEquals("", CameraCredentials.from(anonymous).password());
    }

    @Test
    public void invalidCredentialsDoNotAppearInErrors() {
        CameraConfig invalid = new CameraConfig("bad", "Bad", true, "", 554, "", "", "stream1",
            "rtsp://user:private-password@bad host/stream1");
        try {
            CameraCredentials.from(invalid);
            fail("Malformed account should fail");
        } catch (IllegalArgumentException expected) {
            assertFalse(expected.getMessage().contains("private-password"));
        }
    }

    @Test
    public void removesUiAddedCameraAndKeepsSettingsDisabledCamerasAndRecordings() throws Exception {
        Path path = config("# Keep me\ngrid.columns=3\ncamera.7.enabled=false\ncamera.7.name=Disabled\n");
        CameraConfig first = ConfigLoader.addCamera(path, "rtsp://user:pass@192.0.2.10/stream1", "Front");
        ConfigLoader.addCamera(path, "rtsp://user:pass@192.0.2.11/stream1", "Back");
        AppConfig running = ConfigLoader.load(path);
        Path clip = running.recordingsDir().resolve(first.recordingName()).resolve("clip.mkv");
        Files.createDirectories(clip.getParent());
        Files.write(clip, new byte[] {1, 2, 3});
        ConfigLoader.removeCamera(path, first);
        AppConfig reopened = ConfigLoader.load(path);
        assertEquals(1, reopened.cameras().size());
        assertEquals("Back", reopened.cameras().get(0).displayName());
        assertEquals(3, reopened.gridColumns());
        assertTrue(text(path).contains("# Keep me"));
        assertTrue(text(path).contains("camera.7.enabled=false"));
        assertFalse(text(path).contains("camera.8."));
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(clip));
    }

    @Test
    public void removesExternalUrlOnlyAndCanAddAgainAfterDeletingLastCamera() throws Exception {
        Path urls = temporary.newFile("urls.txt").toPath();
        String header = "# Keep this list\r\n\r\n";
        Files.write(urls, (header + "rtsp://user:pass@192.0.2.10/stream1").getBytes(StandardCharsets.UTF_8));
        Path path = config("rtsp.urls.file=" + urls.toString().replace('\\', '/') + "\n");
        byte[] configBefore = Files.readAllBytes(path);
        ConfigLoader.removeCamera(path, ConfigLoader.load(path).cameras().get(0));
        assertEquals(header, text(urls));
        assertArrayEquals(configBefore, Files.readAllBytes(path));
        assertTrue(ConfigLoader.load(path).cameras().isEmpty());
        ConfigLoader.addCamera(path, "rtsp://new:account@192.0.2.10/stream1", "New");
        assertEquals("New", ConfigLoader.load(path).cameras().get(0).displayName());
    }

    @Test
    public void deletesAcrossMixedSourcesIncludingContinuedAndEscapedProperties() throws Exception {
        Path urls = temporary.newFile("urls.txt").toPath();
        Files.write(urls, "# External\nrtsp://192.0.2.13/stream1\n".getBytes(StandardCharsets.UTF_8));
        Path path = config("# Inline\nrtsp.urls = rtsp://192.0.2.10/stream1 \\\n"
            + "  rtsp://192.0.2.11/stream1\n"
            + "rtsp\\.url\\.5: rtsp://192.0.2.12/stream1\n"
            + "rtsp.urls.file=" + urls.toString().replace('\\', '/') + "\n"
            + "camera.1.enabled=false\ncamera.1.host=192.0.2.99\n"
            + "camera.2.id=last\ncamera.2.host=192.0.2.14\n");
        assertEquals(5, ConfigLoader.load(path).cameras().size());
        for (String host : new String[] {"192.0.2.11", "192.0.2.12", "192.0.2.13", "192.0.2.14", "192.0.2.10"}) {
            List<CameraConfig> cameras = ConfigLoader.load(path).cameras();
            CameraConfig selected = cameras.stream().filter(c -> c.liveUrl().contains(host)).findFirst().get();
            ConfigLoader.removeCamera(path, selected);
            assertEquals(cameras.size() - 1, ConfigLoader.load(path).cameras().size());
        }
        assertTrue(text(path).contains("# Inline"));
        assertTrue(text(path).contains("camera.1.enabled=false"));
        assertEquals("# External\n", text(urls));
    }

    @Test
    public void deletesOnlySelectedDuplicateOccurrenceFromExternalList() throws Exception {
        Path urls = temporary.newFile("duplicates.txt").toPath();
        String url = "rtsp://192.0.2.10/stream1";
        Files.write(urls, (url + "\n# Between\n" + url + "\n").getBytes(StandardCharsets.UTF_8));
        Path path = config("rtsp.urls.file=" + urls.toString().replace('\\', '/') + "\n");
        ConfigLoader.removeCamera(path, ConfigLoader.load(path).cameras().get(1));
        assertEquals(url + "\n# Between\n", text(urls));
        assertEquals(1, ConfigLoader.load(path).cameras().size());
    }

    @Test
    public void staleSelectionLeavesFilesUntouched() throws Exception {
        Path path = config("rtsp.url.1=rtsp://192.0.2.10/stream1\n");
        byte[] before = Files.readAllBytes(path);
        CameraConfig stale = new CameraConfig("missing", "Missing", true, "192.0.2.11", 554,
            "", "", "stream1", "");
        try {
            ConfigLoader.removeCamera(path, stale);
            fail("Stale camera should not delete another camera");
        } catch (IllegalArgumentException expected) {
            assertArrayEquals(before, Files.readAllBytes(path));
        }
    }

    @Test
    public void canRemoveRemainingImportedDuplicateWithoutRestarting() throws Exception {
        Path path = config("rtsp.url.1=rtsp://192.0.2.10/stream1\n"
            + "rtsp.url.2=rtsp://192.0.2.10/stream1\n");
        List<CameraConfig> running = ConfigLoader.load(path).cameras();
        ConfigLoader.removeCamera(path, running.get(0));
        ConfigLoader.removeCamera(path, running.get(1));
        assertTrue(ConfigLoader.load(path).cameras().isEmpty());
    }

    @Test
    public void removingCameraUnregistersRecorderAndReaddingDoesNotRestoreOldName() throws Exception {
        Path path = config("");
        CameraConfig first = ConfigLoader.addCamera(path, "rtsp://192.0.2.10/stream1", "Old name");
        AppConfig running = ConfigLoader.load(path);
        CameraNameStore.save(running.cameraNamesPath(), running.cameras());
        RecorderManager recorder = new RecorderManager(running);
        ConfigLoader.removeCamera(path, first);
        recorder.removeCamera(first);
        Field field = RecorderManager.class.getDeclaredField("recorders");
        field.setAccessible(true);
        assertTrue(((Map<?, ?>) field.get(recorder)).isEmpty());
        ConfigLoader.addCamera(path, "rtsp://192.0.2.10/stream1", "New name");
        assertEquals("New name", ConfigLoader.load(path).cameras().get(0).displayName());
    }
}
