package api.support.http;

import java.net.URL;

public class OkapiHeaders {
  private final URL url;
  private final String tenantId;
  private final String token;
  private final String userId;
  private final String requestId;
  private final String okapiPermissions;

  public OkapiHeaders(URL url, String tenantId, String token, String userId) {
    this(url, tenantId, token, userId, null, "[]");
  }

  public OkapiHeaders(URL url, String tenantId, String token, String userId,
    String requestId, String okapiPermissions) {

    this.url = url;
    this.tenantId = tenantId;
    this.token = token;
    this.userId = userId;
    this.requestId = requestId;
    this.okapiPermissions = okapiPermissions;
  }

  public OkapiHeaders withRequestId(String requestId) {
    return new OkapiHeaders(this.url, this.tenantId, this.token, this.userId,
      requestId, this.okapiPermissions);
  }

  public OkapiHeaders withUserId(String userId) {
    return new OkapiHeaders(this.url, this.tenantId, this.token, userId,
      this.requestId, this.okapiPermissions);
  }

  public OkapiHeaders withOkapiPermissions(String okapiPermissions) {
    return new OkapiHeaders(this.url, this.tenantId, this.token, this.userId,
      this.requestId, okapiPermissions);
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

  public boolean hasUserId() {
    return userId != null;
  }

  public String getRequestId() {
    return requestId;
  }

  public String getOkapiPermissions() {
    return okapiPermissions;
  }
}
