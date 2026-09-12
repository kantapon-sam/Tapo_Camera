package local.tapo.viewer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class CameraConfigurationTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private Path config(String settings) throws IOException {
        Path path = temporary.getRoot().toPath().resolve("selected.properties");
        String recordings = temporary.getRoot().toPath().resolve("recordings").toString().replace('\\', '/');
        Files.write(path, ("recordings.dir=" + recordings + "\n" + settings).getBytes(StandardCharsets.UTF_8));
        return path;
    }

    @Test
    public void addPersistsInSelectedConfigAndPreservesExistingSettings() throws Exception {
        Path path = config("# Keep this comment\ngrid.columns=3\nlive.autoStart=false\n"
            + "rtsp.url.1=rtsp://original:secret@192.168.1.51:554/stream1\n"
            + "camera.7.enabled=false\ncamera.7.name=Disabled camera\n");
        byte[] original = Files.readAllBytes(path);
        AppConfig running = ConfigLoader.load(path);
        String url = RtspUrlBuilder.forNewCamera("192.168.1.54", 554, "stream1", "", "", running.cameras().get(0));
        CameraConfig added = ConfigLoader.addCamera(running.configPath(), url, "Front door");
        AppConfig reopened = ConfigLoader.load(path);

        assertEquals(path.toAbsolutePath(), running.configPath());
        assertEquals(2, reopened.cameras().size());
        assertEquals(3, reopened.gridColumns());
        assertFalse(reopened.liveAutoStart());
        assertEquals("Front door", reopened.cameras().get(1).displayName());
        assertEquals(added.id(), reopened.cameras().get(1).id());
        assertEquals(url, reopened.cameras().get(1).liveUrl());
        String stored = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        assertTrue(stored.startsWith(new String(original, StandardCharsets.UTF_8)));
        assertTrue(stored.contains("camera.8.enabled=true"));
        assertFalse(Files.exists(path.resolveSibling("cameras.properties")));
    }

    @Test
    public void rejectsDuplicateFromExternalUrlFileRegardlessOfCredentialsOrDefaultPort() throws Exception {
        Path urls = temporary.newFile("urls.txt").toPath();
        Files.write(urls, "rtsp://old:secret@192.168.1.54/stream1\n".getBytes(StandardCharsets.UTF_8));
        Path path = config("rtsp.urls.file=" + urls.toString().replace('\\', '/') + "\n");
        byte[] before = Files.readAllBytes(path);
        try {
            ConfigLoader.addCamera(path, "rtsp://different:account@192.168.1.54:554/stream1", "duplicate");
            fail("Duplicate should be rejected");
        } catch (IllegalArgumentException expected) {
            assertFalse(expected.getMessage().contains("secret"));
        }
        assertArrayEquals(before, Files.readAllBytes(path));
        assertEquals(1, ConfigLoader.load(path).cameras().size());
    }

    @Test
    public void reusesEscapedAccountWithoutDoubleEncoding() throws Exception {
        String accountUrl = "rtsp://u%40ser:p%3Aa%2Bss%25%5C@192.168.1.53:554/stream1";
        CameraConfig template = new CameraConfig("old", "old", true, "", 554, "", "", "stream1", accountUrl);
        String added = RtspUrlBuilder.forNewCamera("192.168.1.54", 554, "stream2", "ignored", "ignored", template);
        assertEquals(new URI(accountUrl).getRawUserInfo(), new URI(added).getRawUserInfo());
        assertEquals("192.168.1.54", new URI(added).getHost());
        assertEquals("/stream2", new URI(added).getPath());
    }

    @Test
    public void customAccountAndUnicodeNameRoundTrip() throws Exception {
        Path path = config("");
        String url = RtspUrlBuilder.forNewCamera("192.168.1.54", 8554, "stream2", "u@ser", "p:a ss+%\\", null);
        String name = "\u0e2b\u0e19\u0e49\u0e32\u0e23\u0e49\u0e32\u0e19";
        ConfigLoader.addCamera(path, url, name);
        CameraConfig reopened = ConfigLoader.load(path).cameras().get(0);
        assertEquals(url, reopened.liveUrl());
        assertEquals("u@ser:p:a ss+%\\", new URI(reopened.liveUrl()).getUserInfo());
        assertEquals(name, reopened.displayName());
    }

    @Test
    public void allowsDistinctStreamAndKeepsRecordingNamesUnique() throws Exception {
        Path path = config("");
        CameraConfig first = ConfigLoader.addCamera(path, "rtsp://192.168.1.54/stream1", "Shop");
        CameraConfig second = ConfigLoader.addCamera(path, "rtsp://192.168.1.54/stream2", "Shop");
        assertNotEquals(first.id(), second.id());
        assertNotEquals(first.recordingName(), second.recordingName());
        AppConfig reopened = ConfigLoader.load(path);
        assertEquals(second.recordingName(), reopened.cameras().get(1).recordingName());
    }

    @Test
    public void rejectsMalformedHostAndPortWithoutLeakingAccount() {
        for (String host : new String[] {"", "192.168.1.54/stream1", "rtsp://192.168.1.54",
                "bad host", "user@192.168.1.54", "192.168.1.54:554", "192.168.1.54?x=1"}) {
            try {
                RtspUrlBuilder.forNewCamera(host, 554, "stream1", "user", "topsecret", null);
                fail("Invalid host accepted: " + host);
            } catch (IllegalArgumentException expected) {
                assertFalse(expected.getMessage().contains("topsecret"));
            }
        }
        for (int port : new int[] {-1, 0, 65536}) {
            try {
                RtspUrlBuilder.forNewCamera("192.168.1.54", port, "stream1", "", "", null);
                fail("Invalid port accepted");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("Port"));
            }
        }
    }

    @Test
    public void invalidUrlLeavesConfigUnchanged() throws Exception {
        Path path = config("");
        byte[] before = Files.readAllBytes(path);
        try {
            ConfigLoader.addCamera(path, "http://user:secret@192.168.1.54/stream1", "Bad");
            fail("Non RTSP URL should be rejected");
        } catch (IllegalArgumentException expected) {
            assertFalse(expected.getMessage().contains("secret"));
        }
        assertArrayEquals(before, Files.readAllBytes(path));
    }

    @Test
    public void emptyConfigCanAcceptFirstCameraAndRegisterForRecording() throws Exception {
        Path path = config("");
        AppConfig running = ConfigLoader.load(path);
        assertTrue(running.cameras().isEmpty());
        RecorderManager recorder = new RecorderManager(running);
        CameraConfig added = ConfigLoader.addCamera(path, "rtsp://192.168.1.54/stream1", "");
        running.cameras().add(added);
        recorder.addCamera(added);
        recorder.addCamera(added);
        Field field = RecorderManager.class.getDeclaredField("recorders");
        field.setAccessible(true);
        Map<?, ?> recorders = (Map<?, ?>) field.get(recorder);
        assertEquals(1, recorders.size());
        assertTrue(recorders.containsKey(added.id()));
        assertEquals("192.168.1.54/stream1", added.displayName());
    }
}
