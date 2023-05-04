package org.folio.circulation.support.http;

import static java.util.Collections.unmodifiableSet;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toSet;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.map.CaseInsensitiveMap;

import io.vertx.core.json.JsonArray;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class OkapiPermissions {
  private final Set<String> permissions;

  private OkapiPermissions(Set<String> permissions) {
    this.permissions = unmodifiableSet(permissions);
  }

  public static OkapiPermissions from(Map<String, String> okapiHeaders) {
    return new OkapiPermissions(getPermissionsFromHeaders(okapiHeaders));
  }

  public static OkapiPermissions of(String... permissions) {
    return new OkapiPermissions(Set.of(permissions));
  }

  private static Set<String> getPermissionsFromHeaders(Map<String, String> okapiHeaders) {
    String permissionsString = new CaseInsensitiveMap<>(okapiHeaders)
      .getOrDefault(OkapiHeader.OKAPI_PERMISSIONS, "[]");

    return new JsonArray(permissionsString)
      .stream()
      .filter(String.class::isInstance)
      .map(String.class::cast)
      .collect(Collectors.toSet());
  }

  public Set<String> getPermissions() {
    return Set.copyOf(permissions);
  }

  public boolean isEmpty() {
    return permissions.isEmpty();
  }

  public static OkapiPermissions empty() {
    return of();
  }

  public boolean contains(String permission) {
    return permissions.contains(permission);
  }

  public OkapiPermissions getAllNotContainedIn(OkapiPermissions other) {
    if (other == null) {
      return new OkapiPermissions(permissions);
    }

    return permissions.stream()
      .filter(not(other::contains))
      .collect(collectingAndThen(toSet(), OkapiPermissions::new));
  }

}
