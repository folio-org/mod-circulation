package api.support.builders;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class LocationBuilder extends JsonBuilder implements Builder {

  private final String name;
  private final String code;
  private final String discoveryDisplayName;
  private final UUID institutionId;
  private final UUID campusId;
  private final UUID libraryId;
  private final UUID primaryServicePointId;
  private final Set<UUID> otherServicePointIds;
  private final String effectiveLocationPrimaryServicePointName;
  private final Boolean isFloatingCollection;

  public LocationBuilder() {
    this(null, null, null, null, null, null, null, new HashSet<>(), null, false);
  }

  private LocationBuilder(
    String name,
    String code,
    String discoveryDisplayName,
    UUID institutionId,
    UUID campusId,
    UUID libraryId,
    UUID primaryServicePointId,
    Set<UUID> otherServicePointIds,
    String effectiveLocationPrimaryServicePointName,
    boolean isFloatingCollection) {

    if (otherServicePointIds == null) {
      otherServicePointIds = new HashSet<>();
    }

    this.name = name;
    this.code = code;
    this.discoveryDisplayName = discoveryDisplayName;
    this.institutionId = institutionId;
    this.campusId = campusId;
    this.libraryId = libraryId;
    this.primaryServicePointId = primaryServicePointId;
    this.otherServicePointIds = otherServicePointIds;
    this.effectiveLocationPrimaryServicePointName = effectiveLocationPrimaryServicePointName;
    this.isFloatingCollection = isFloatingCollection;
  }

  @Override
  public JsonObject create() {
    final JsonObject representation = new JsonObject();

    write(representation, "name", name);
    write(representation, "code",  code);
    write(representation, "discoveryDisplayName", discoveryDisplayName);
    write(representation, "institutionId", institutionId);
    write(representation, "campusId", campusId);
    write(representation, "libraryId", libraryId);
    write(representation, "primaryServicePoint", primaryServicePointId);

    if(otherServicePointIds != null && !otherServicePointIds.isEmpty()) {
      final JsonArray mappedServicePointIds = new JsonArray(otherServicePointIds
                                                          .stream()
                                                          .filter(Objects::nonNull)
                                                          .map(UUID::toString)
                                                          .collect(Collectors.toList()));

      write(representation, "servicePointIds", mappedServicePointIds);
      write(representation, "effectiveLocationPrimaryServicePointName", effectiveLocationPrimaryServicePointName);
    }
    write(representation, "isFloatingCollection", isFloatingCollection);

    return representation;
  }

  public LocationBuilder withName(String name) {
    return new LocationBuilder(
      name,
      this.code,
      this.discoveryDisplayName,
      this.institutionId,
      this.campusId,
      this.libraryId,
      this.primaryServicePointId,
      this.otherServicePointIds,
      this.effectiveLocationPrimaryServicePointName,
      this.isFloatingCollection);
  }

  public LocationBuilder withCode(String code) {
    return new LocationBuilder(
      this.name,
      code,
      this.discoveryDisplayName,
      this.institutionId,
      this.campusId,
      this.libraryId,
      this.primaryServicePointId,
      this.otherServicePointIds,
      this.effectiveLocationPrimaryServicePointName,
      this.isFloatingCollection);
  }

  public LocationBuilder forInstitution(UUID institutionId) {
    return new LocationBuilder(
      this.name,
      this.code,
      this.discoveryDisplayName,
      institutionId,
      this.campusId,
      this.libraryId,
      this.primaryServicePointId,
      this.otherServicePointIds,
      this.effectiveLocationPrimaryServicePointName,
      this.isFloatingCollection);
  }

  public LocationBuilder forCampus(UUID campusId) {
    return new LocationBuilder(
      this.name,
      this.code,
      this.discoveryDisplayName,
      this.institutionId,
      campusId,
      this.libraryId,
      this.primaryServicePointId,
      this.otherServicePointIds,
      this.effectiveLocationPrimaryServicePointName,
      this.isFloatingCollection);
  }

  public LocationBuilder forLibrary(UUID libraryId) {
    return new LocationBuilder(
      this.name,
      this.code,
      this.discoveryDisplayName,
      this.institutionId,
      this.campusId,
      libraryId,
      this.primaryServicePointId,
      this.otherServicePointIds,
      this.effectiveLocationPrimaryServicePointName,
      this.isFloatingCollection);
  }

  public LocationBuilder withPrimaryServicePoint(UUID primaryServicePointId) {
    return new LocationBuilder(
      this.name,
      this.code,
      this.discoveryDisplayName,
      this.institutionId,
      this.campusId,
      this.libraryId,
      primaryServicePointId,
      this.otherServicePointIds,
      this.effectiveLocationPrimaryServicePointName,
      this.isFloatingCollection)
      .servedBy(primaryServicePointId);
  }

  public LocationBuilder withDiscoveryDisplayName(String discoveryDisplayName) {
    return new LocationBuilder(
      this.name,
      this.code,
      discoveryDisplayName,
      this.institutionId,
      this.campusId,
      this.libraryId,
      this.primaryServicePointId,
      this.otherServicePointIds,
      this.effectiveLocationPrimaryServicePointName,
      this.isFloatingCollection);
  }

  public LocationBuilder withEffectiveLocationPrimaryServicePointName(String effectiveLocationPrimaryServicePointName) {
    return new LocationBuilder(
      this.name,
      this.code,
      this.discoveryDisplayName,
      this.institutionId,
      this.campusId,
      this.libraryId,
      primaryServicePointId,
      this.otherServicePointIds,
      effectiveLocationPrimaryServicePointName,
      this.isFloatingCollection)
      .servedBy(primaryServicePointId);
  }

  public LocationBuilder isFloatingCollection(boolean isFloatingCollection) {
    return new LocationBuilder(
      this.name,
      this.code,
      this.discoveryDisplayName,
      this.institutionId,
      this.campusId,
      this.libraryId,
      primaryServicePointId,
      this.otherServicePointIds,
      this.effectiveLocationPrimaryServicePointName,
      isFloatingCollection
    );
  }

  public LocationBuilder servedBy(UUID servicePointId) {
    final HashSet<UUID> servicePoints = new HashSet<>(otherServicePointIds);

    servicePoints.add(servicePointId);

    return servedBy(servicePoints);
  }

  public LocationBuilder servedBy(Set<UUID> servicePoints) {
    final HashSet<UUID> newServicePointIds = new HashSet<>(this.otherServicePointIds);

    newServicePointIds.addAll(servicePoints);

    return new LocationBuilder(
      this.name,
      this.code,
      this.discoveryDisplayName,
      this.institutionId,
      this.campusId,
      this.libraryId,
      this.primaryServicePointId,
      newServicePointIds,
      this.effectiveLocationPrimaryServicePointName,
      this.isFloatingCollection);
  }
}
