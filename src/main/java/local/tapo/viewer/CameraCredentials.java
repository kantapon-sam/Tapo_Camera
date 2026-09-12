package local.tapo.viewer;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

final class CameraCredentials {
    private final String username;
    private final String password;

    private CameraCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    static CameraCredentials from(CameraConfig camera) {
        String account = RtspUrlBuilder.validate(camera.liveUrl()).getRawUserInfo();
        if (account == null) {
            return new CameraCredentials("", "");
        }
        int separator = account.indexOf(':');
        return new CameraCredentials(decode(separator < 0 ? account : account.substring(0, separator)),
            separator < 0 ? "" : decode(account.substring(separator + 1)));
    }

    private static String decode(String value) {
        try {
            // A literal '+' in URI user info is not a form-encoded space.
            return URLDecoder.decode(value.replace("+", "%2B"), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is not available", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The camera account could not be read.");
        }
    }

    String username() {
        return username;
    }

    String password() {
        return password;
    }
}
