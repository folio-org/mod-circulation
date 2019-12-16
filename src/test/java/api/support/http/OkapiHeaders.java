package api.support.http;

import java.net.URL;

public class OkapiHeaders {
  private final URL url;
  private final String tenantId;
  private final String token;
  private final String userId;
  private final String requestId;

  public OkapiHeaders(URL url, String tenantId, String token, String userId) {
    this(url, tenantId, token, userId, null);
  }

  public OkapiHeaders(URL url, String tenantId, String token, String userId,
      String requestId) {

    this.url = url;
    this.tenantId = tenantId;
    this.token = token;
    this.userId = userId;
    this.requestId = requestId;
  }

  public OkapiHeaders withRequestId(String requestId) {
    return new OkapiHeaders(this.url, this.tenantId, this.token, this.userId,
      requestId);
  }

  public URL getUrl() {
    return url;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getToken() {
    return token;
  }

  public String getUserId() {
    return userId;
  }

  public String getRequestId() {
    return requestId;
  }
}
