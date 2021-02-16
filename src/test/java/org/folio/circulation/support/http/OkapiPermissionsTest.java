package org.folio.circulation.support.http;

import static java.util.Collections.emptyMap;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class OkapiPermissionsTest {
  private static final String PERMISSIONS_HEADER = "X-Okapi-Permissions";
  private static final String PERMISSION_1 = "users.item.get";
  private static final String PERMISSION_2 = "pubsub.publish.post";
  private static final String PERMISSIONS_STRING =
    String.format("[\"%s\", \"%s\"]", PERMISSION_1, PERMISSION_2);
  private static final Map<String, String> HEADERS_MAP =
    Map.of(PERMISSIONS_HEADER, PERMISSIONS_STRING);

  @Test
  public void fromCreatesInstanceWithAllPermissionsFromMap() {
    OkapiPermissions permissions = OkapiPermissions.from(HEADERS_MAP);
    Set<String> permissionSet = permissions.getPermissions();

    assertThat(permissionSet, hasSize(2));
    assertThat(permissionSet, hasItems(PERMISSION_1, PERMISSION_2));
  }

  @Test
  public void fromCreatesInstanceWithAllPermissionsFromMapIsLowercase() {
    OkapiPermissions permissions = OkapiPermissions.from(
      Map.of(PERMISSIONS_HEADER.toLowerCase(), PERMISSIONS_STRING));

    Set<String> permissionSet = permissions.getPermissions();

    assertThat(permissionSet, hasSize(2));
    assertThat(permissionSet, hasItems(PERMISSION_1, PERMISSION_2));
  }

  @Test
  public void fromCreatesEmptyInstanceWhenPermissionsArrayIsEmpty() {
    OkapiPermissions permissions = OkapiPermissions.from(Map.of(PERMISSIONS_HEADER, "[]"));

    assertTrue(permissions.isEmpty());
  }

  @Test
  public void fromCreatesEmptyInstanceWhenPermissionsHeaderIsMissing() {
    OkapiPermissions permissions = OkapiPermissions.from(emptyMap());

    assertTrue(permissions.isEmpty());
  }
  
  @Test
  public void ofCreatesNewInstanceWithAllPassedPermissions() {
    OkapiPermissions permissions = OkapiPermissions.of(PERMISSION_1, PERMISSION_2);

    assertThat(permissions.getPermissions().size(), is(2));
    assertThat(permissions.getPermissions(), hasItems(PERMISSION_1, PERMISSION_2));
  }

  @Test
  public void ofCreatesEmptyInstanceWhenNoArgumentsArePassed() {
    OkapiPermissions permissions = OkapiPermissions.of();

    assertTrue(permissions.isEmpty());
  }

  @Test
  public void emptyCreatesEmptyInstance() {
    OkapiPermissions permissions = OkapiPermissions.empty();

    assertTrue(permissions.isEmpty());
  }

  @Test
  public void getAllNotContainedInReturnsEmptyInstanceWhenPermissionSetsAreEqual() {
    OkapiPermissions permissions1 = OkapiPermissions.of(PERMISSION_1, PERMISSION_2);
    OkapiPermissions permissions2 = OkapiPermissions.of(PERMISSION_1, PERMISSION_2);

    OkapiPermissions result = permissions1.getAllNotContainedIn(permissions2);
    assertTrue(result.isEmpty());
  }

  @Test
  public void getAllNotContainedInReturnsEmptyInstanceWhenPermissionSetsAreEmpty() {
    OkapiPermissions permissions1 = OkapiPermissions.empty();
    OkapiPermissions permissions2 = OkapiPermissions.empty();

    OkapiPermissions result = permissions1.getAllNotContainedIn(permissions2);
    assertTrue(result.isEmpty());
  }

  @Test
  public void getAllNotContainedInReturnsEmptyInstanceWhenSourceIsEmpty() {
    OkapiPermissions permissions1 = OkapiPermissions.empty();
    OkapiPermissions permissions2 = OkapiPermissions.of(PERMISSION_1, PERMISSION_2);

    OkapiPermissions result = permissions1.getAllNotContainedIn(permissions2);
    assertTrue(result.isEmpty());
  }

  @Test
  public void getAllNotContainedInReturnsNewInstanceWithAllPermissionsFromSourceWhenArgumentIsEmpty() {
    OkapiPermissions permissions1 = OkapiPermissions.of(PERMISSION_1, PERMISSION_2);
    OkapiPermissions permissions2 = OkapiPermissions.empty();
    OkapiPermissions result = permissions1.getAllNotContainedIn(permissions2);

    assertThat(result.getPermissions().size(), is(2));
    assertThat(result.getPermissions(), hasItems(PERMISSION_1, PERMISSION_2));
    assertNotSame(permissions1, result);
  }

  @Test
  public void getAllNotContainedInReturnsNewInstanceWithAllPermissionsFromSourceWhenArgumentIsNull() {
    OkapiPermissions permissions1 = OkapiPermissions.of(PERMISSION_1, PERMISSION_2);
    OkapiPermissions result = permissions1.getAllNotContainedIn(null);

    assertThat(result.getPermissions().size(), is(2));
    assertThat(result.getPermissions(), hasItems(PERMISSION_1, PERMISSION_2));
    assertNotSame(permissions1, result);
  }

}