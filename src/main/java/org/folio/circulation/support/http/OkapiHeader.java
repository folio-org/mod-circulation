package org.folio.circulation.support.http;

public class OkapiHeader {
  private OkapiHeader() { }

  public static final String OKAPI_URL = "X-Okapi-Url";
  public static final String TENANT = "X-Okapi-Tenant";
  public static final String TOKEN = "X-Okapi-Token";
  public static final String USER_ID = "X-Okapi-User-Id";
  public static final String REQUEST_ID = "X-Okapi-Request-Id";
}
