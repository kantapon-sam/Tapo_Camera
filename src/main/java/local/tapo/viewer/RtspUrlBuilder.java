package local.tapo.viewer;

import java.net.URLEncoder;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

public final class RtspUrlBuilder {
    private RtspUrlBuilder() {
    }

    public static String build(String username, String password, String host, int port, String stream) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Camera host is required when camera.rtsp is not set");
        }
        String cleanStream = stream == null || stream.trim().isEmpty() ? "stream1" : stream.replaceFirst("^/+", "");
        String userInfo = "";
        if (username != null && !username.trim().isEmpty()) {
            userInfo = encodeUserInfo(username) + ":" + encodeUserInfo(password == null ? "" : password) + "@";
        }
        return "rtsp://" + userInfo + host + ":" + port + "/" + cleanStream;
    }

    public static String forNewCamera(String host, int port, String stream, String username,
                                      String password, CameraConfig accountSource) {
        String cleanHost = host == null ? "" : host.trim();
        try {
            URI hostCheck = new URI("rtsp://" + cleanHost);
            if (cleanHost.isEmpty() || hostCheck.getHost() == null || hostCheck.getPort() != -1
                || hostCheck.getRawUserInfo() != null || !hostCheck.getRawPath().isEmpty()
                || hostCheck.getRawQuery() != null || hostCheck.getRawFragment() != null) {
                throw new IllegalArgumentException("Enter a camera IP address or hostname only.");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Enter a valid camera IP address or hostname.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535.");
        }

        String url;
        if (accountSource == null) {
            url = build(username, password, cleanHost, port, stream);
        } else {
            String rawAccount;
            try {
                rawAccount = new URI(accountSource.liveUrl()).getRawUserInfo();
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("The selected camera account could not be read.");
            }
            String base = build("", "", cleanHost, port, stream);
            url = rawAccount == null ? base : "rtsp://" + rawAccount + "@" + base.substring(7);
        }
        validate(url);
        return url;
    }

    static URI validate(String url) {
        try {
            URI uri = new URI(url);
            if ((!"rtsp".equalsIgnoreCase(uri.getScheme()) && !"rtsps".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getPort() == 0 || uri.getPort() > 65535
                || uri.getFragment() != null) {
                throw new IllegalArgumentException("Enter a valid RTSP camera address.");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Enter a valid RTSP camera address.");
        }
    }

    private static String encodeUserInfo(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is not available", e);
        }
    }
}
