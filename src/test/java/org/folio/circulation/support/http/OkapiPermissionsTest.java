package org.folio.circulation.support.http;

import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;

class OkapiPermissionsTest {
  private static final String PERMISSIONS_HEADER = "X-Okapi-Permissions";
  private static final String PERMISSION_1 = "users.item.get";
  private static final String PERMISSION_2 = "pubsub.publish.post";
  private static final String PERMISSIONS_STRING =
    new JsonArray(List.of(PERMISSION_1, PERMISSION_2)).encode();
  private static final Map<String, String> HEADERS_MAP =
    Map.of(PERMISSIONS_HEADER, PERMISSIONS_STRING);

  @Test
  void fromCreatesInstanceWithAllPermissionsFromMap() {
    OkapiPermissions permissions = OkapiPermissions.from(HEADERS_MAP);
    Set<String> permissionSet = permissions.getPermissions();

    assertThat(permissionSet, hasSize(2));
    assertThat(permissionSet, hasItems(PERMISSION_1, PERMISSION_2));
  }

  @Test
  void fromCreatesInstanceWithAllPermissionsFromMapWhenHeaderIsLowercase() {
    OkapiPermissions permissions = OkapiPermissions.from(
      Map.of(PERMISSIONS_HEADER.toLowerCase(), PERMISSIONS_STRING));

    Set<String> permissionSet = permissions.getPermissions();

    assertThat(permissionSet, hasSize(2));
    assertThat(permissionSet, hasItems(PERMISSION_1, PERMISSION_2));
  }

  @Test
  void fromCreatesEmptyInstanceWhenPermissionsArrayIsEmpty() {
    OkapiPermissions permissions = OkapiPermissions.from(Map.of(PERMISSIONS_HEADER, "[]"));

    assertTrue(permissions.isEmpty());
  }

  @Test
  void fromCreatesEmptyInstanceWhenPermissionsHeaderIsMissing() {
    OkapiPermissions permissions = OkapiPermissions.from(emptyMap());

    assertTrue(permissions.isEmpty());
  }

  @Test
  void ofCreatesNewInstanceWithAllPassedPermissions() {
    OkapiPermissions permissions = OkapiPermissions.of(PERMISSION_1, PERMISSION_2);

    assertThat(permissions.getPermissions().size(), is(2));
    assertThat(permissions.getPermissions(), hasItems(PERMISSION_1, PERMISSION_2));
  }

  @Test
  void ofCreatesEmptyInstanceWhenNoArgumentsArePassed() {
    OkapiPermissions permissions = OkapiPermissions.of();

    assertTrue(permissions.isEmpty());
  }

  @Test
  void emptyCreatesEmptyInstance() {
    OkapiPermissions permissions = OkapiPermissions.empty();

    assertTrue(permissions.isEmpty());
  }

  @Test
  void getAllNotContainedInReturnsEmptyInstanceWhenPermissionSetsAreEqual() {
    OkapiPermissions permissions1 = OkapiPermissions.of(PERMISSION_1, PERMISSION_2);
    OkapiPermissions permissions2 = OkapiPermissions.of(PERMISSION_1, PERMISSION_2);

    OkapiPermissions result = permissions1.getAllNotContainedIn(permissions2);
    assertTrue(result.isEmpty());
  }

  @Test
  void getAllNotContainedInReturnsEmptyInstanceWhenPermissionSetsAreEmpty() {
    OkapiPermissions permissions1 = OkapiPermissions.empty();
    OkapiPermissions permissions2 = OkapiPermissions.empty();

    OkapiPermissions result = permissions1.getAllNotContainedIn(permissions2);
    assertTrue(result.isEmpty());
  }

  @Test
  void getAllNotContainedInReturnsEmptyInstanceWhenSourceIsEmpty() {
    OkapiPermissions permissions1 = OkapiPermissions.empty();
    OkapiPermissions permissions2 = OkapiPermissions.of(PERMISSION_1, PERMISSION_2);

    OkapiPermissions result = permissions1.getAllNotContainedIn(permissions2);
    assertTrue(result.isEmpty());
  }

  @Test
  void getAllNotContainedInReturnsNewInstanceWithAllPermissionsFromSourceWhenArgumentIsEmpty() {
    OkapiPermissions permissions1 = OkapiPermissions.of(PERMISSION_1, PERMISSION_2);
    OkapiPermissions permissions2 = OkapiPermissions.empty();
    OkapiPermissions result = permissions1.getAllNotContainedIn(permissions2);

    assertThat(result.getPermissions().size(), is(2));
    assertThat(result.getPermissions(), hasItems(PERMISSION_1, PERMISSION_2));
    assertNotSame(permissions1, result);
  }

  @Test
  void getAllNotContainedInReturnsNewInstanceWithAllPermissionsFromSourceWhenArgumentIsNull() {
    OkapiPermissions permissions1 = OkapiPermissions.of(PERMISSION_1, PERMISSION_2);
    OkapiPermissions result = permissions1.getAllNotContainedIn(null);

    assertThat(result.getPermissions().size(), is(2));
    assertThat(result.getPermissions(), hasItems(PERMISSION_1, PERMISSION_2));
    assertNotSame(permissions1, result);
  }

}
