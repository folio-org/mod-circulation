package org.folio.circulation.api.support;

import java.net.MalformedURLException;
import java.net.URL;

public class UrlHelper {
  public static URL joinPath(URL base, String additionalPath)
    throws MalformedURLException {

    return new URL(
      base.getProtocol(),
      base.getHost(),
      base.getPort(),
      base.getPath() + additionalPath);
  }
}
