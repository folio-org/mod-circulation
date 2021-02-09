package org.folio.circulation.support.utils;

import static java.util.Collections.emptyMap;
import static org.folio.circulation.support.utils.OkapiHeadersUtils.getOkapiPermissions;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class OkapiHeadersUtilsTest {
  private static final String PERMISSIONS_HEADER = "X-Okapi-Permissions";
  private static final String PERMISSION_1 = "users.item.get";
  private static final String PERMISSION_2 = "pubsub.publish.post";
  private static final String PERMISSIONS_STRING =
    String.format("[\"%s\", \"%s\"]", PERMISSION_1, PERMISSION_2);
  private static final Map<String, String> HEADERS_MAP =
    Map.of(PERMISSIONS_HEADER, PERMISSIONS_STRING);

  @Test
  public void getOkapiPermissionsReturnsListOfPermissions() {
    List<String> okapiPermissions = getOkapiPermissions(HEADERS_MAP);

    assertThat(okapiPermissions, hasSize(2));
    assertThat(okapiPermissions, hasItems(PERMISSION_1, PERMISSION_2));
  }

  @Test
  public void getOkapiPermissionsReturnsListOfPermissionsWhenHeaderIsLowercase() {
    List<String> okapiPermissions = getOkapiPermissions(
      Map.of(PERMISSIONS_HEADER.toLowerCase(), PERMISSIONS_STRING));

    assertThat(okapiPermissions, hasSize(2));
    assertThat(okapiPermissions, hasItems(PERMISSION_1, PERMISSION_2));
  }

  @Test
  public void getOkapiPermissionsReturnsEmptyListWhenPermissionsArrayIsEmpty() {
    List<String> okapiPermissions = getOkapiPermissions(Map.of(PERMISSIONS_HEADER, "[]"));

    assertThat(okapiPermissions, hasSize(0));
  }

  @Test
  public void getOkapiPermissionsReturnsEmptyListPermissionsHeaderIsMissing() {
    List<String> okapiPermissions = getOkapiPermissions(emptyMap());

    assertThat(okapiPermissions, hasSize(0));
  }

}