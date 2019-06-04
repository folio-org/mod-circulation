package api.support.builders;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class LocationBuilder extends JsonBuilder implements Builder {

  private final String name;
  private final String code;
  private final UUID institutionId;
  private final UUID campusId;
  private final UUID libraryId;
  private final UUID primaryServicePointId;
  private final Set<UUID> servicePointIds;

  public LocationBuilder() {
    this(null, null, null, null, null, null, new HashSet<>());
  }

  private LocationBuilder(
    String name,
    String code,
    UUID institutionId,
    UUID campusId,
    UUID libraryId,
    UUID primaryServicePointId,
    Set<UUID> servicePointIds) {

    this.name = name;
    this.code = code;
    this.institutionId = institutionId;
    this.campusId = campusId;
    this.libraryId = libraryId;
    this.primaryServicePointId = primaryServicePointId;
    this.servicePointIds = servicePointIds;
  }

  @Override
  public JsonObject create() {
    final JsonObject representation = new JsonObject();

    write(representation, "name", name);
    write(representation, "code",  code);
    write(representation, "institutionId", institutionId);
    write(representation, "campusId", campusId);
    write(representation, "libraryId", libraryId);
    write(representation, "primaryServicePoint", primaryServicePointId);

    if(servicePointIds != null && !servicePointIds.isEmpty()) {

      final JsonArray mappedServicePointIds = new JsonArray( servicePointIds
                                                          .stream()
                                                          .map(svpt -> svpt.toString())
                                                          .collect(Collectors.toList()));
      write(representation, "servicePointIds", mappedServicePointIds);
    }

    return representation;
  }

  public LocationBuilder withName(String name) {
    return new LocationBuilder(
      name,
      this.code,
      this.institutionId,
      this.campusId,
      this.libraryId,
      this.primaryServicePointId,
      this.servicePointIds);
  }

  public LocationBuilder withCode(String code) {
    return new LocationBuilder(
      this.name,
      code,
      this.institutionId,
      this.campusId,
      this.libraryId,
      this.primaryServicePointId,
      this.servicePointIds);
  }

  public LocationBuilder forInstitution(UUID institutionId) {
    return new LocationBuilder(
      this.name,
      this.code,
      institutionId,
      this.campusId,
      this.libraryId,
      this.primaryServicePointId,
      this.servicePointIds);
  }

  public LocationBuilder forCampus(UUID campusId) {
    return new LocationBuilder(
      this.name,
      this.code,
      this.institutionId,
      campusId,
      this.libraryId,
      this.primaryServicePointId,
      this.servicePointIds);
  }

  public LocationBuilder forLibrary(UUID libraryId) {
    return new LocationBuilder(
      this.name,
      this.code,
      this.institutionId,
      this.campusId,
      libraryId,
      this.primaryServicePointId,
      this.servicePointIds);
  }

  public LocationBuilder withPrimaryServicePoint(UUID primaryServicePointId) {
    return new LocationBuilder(
      this.name,
      this.code,
      this.institutionId,
      this.campusId,
      this.libraryId,
      primaryServicePointId,
      this.servicePointIds)
      .servedBy(primaryServicePointId);
  }

  public LocationBuilder servedBy(UUID servicePointId) {
    final HashSet<UUID> servicePoints
      = new HashSet<>(servicePointIds);

    servicePoints.add(servicePointId);

    return new LocationBuilder(
      this.name,
      this.code,
      this.institutionId,
      this.campusId,
      this.libraryId,
      this.primaryServicePointId,
      servicePoints);
  }

  public LocationBuilder withServicePoints(HashSet<UUID> servicePoints) {
    return new LocationBuilder(
      this.name,
      this.code,
      this.institutionId,
      this.campusId,
      this.libraryId,
      this.primaryServicePointId,
      servicePoints);
  }
}
