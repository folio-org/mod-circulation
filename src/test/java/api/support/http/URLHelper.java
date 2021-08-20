package api.support.http;

import java.net.MalformedURLException;
import java.net.URL;

public class URLHelper {
  public static URL joinPath(URL base, String additionalPath)
    throws MalformedURLException {

    return new URL(
      base.getProtocol(),
      base.getHost(),
      base.getPort(),
      base.getPath() + additionalPath);
  }
}
