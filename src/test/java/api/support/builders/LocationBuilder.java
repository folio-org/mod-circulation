package api.support.builders;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class LocationBuilder extends JsonBuilder implements Builder {

  private final String name;
  private final String code;
  private final String libraryName;
  private final UUID institutionId;
  private final UUID campusId;
  private final UUID libraryId;
  private final UUID primaryServicePointId;
  private final Set<UUID> servingServicePointIds;

  public LocationBuilder() {
    this(null, null,null, null, null, null, null, new HashSet<>());
  }

  private LocationBuilder(
    String name,
    String code,
    String libraryName,
    UUID institutionId,
    UUID campusId,
    UUID libraryId,
    UUID primaryServicePointId,
    Set<UUID> servingServicePointIds) {

    this.name = name;
    this.code = code;
    this.libraryName = libraryName;
    this.institutionId = institutionId;
    this.campusId = campusId;
    this.libraryId = libraryId;
    this.primaryServicePointId = primaryServicePointId;
    this.servingServicePointIds = servingServicePointIds;
  }

  @Override
  public JsonObject create() {
    final JsonObject representation = new JsonObject();

    write(representation, "name", name);
    write(representation, "code",  code);
    write(representation, "libraryName", libraryName);
    write(representation, "institutionId", institutionId);
    write(representation, "campusId", campusId);
    write(representation, "libraryId", libraryId);
    write(representation, "primaryServicePoint", primaryServicePointId);

    if(!servingServicePointIds.isEmpty()) {
      final JsonArray mappedServicePointIds
        = new JsonArray(new ArrayList<>(servingServicePointIds));

      write(representation, "servicePointIds", mappedServicePointIds);
    }

    return representation;
  }

  public LocationBuilder withName(String name) {
    return new LocationBuilder(
      name,
      this.code,
      this.libraryName,
      this.institutionId,
      this.campusId,
      this.libraryId,
      this.primaryServicePointId,
      this.servingServicePointIds);
  }

  public LocationBuilder withCode(String code) {
    return new LocationBuilder(
      this.name,
      code,
      this.libraryName,
      this.institutionId,
      this.campusId,
      this.libraryId,
      this.primaryServicePointId,
      this.servingServicePointIds);
  }

  public LocationBuilder withLibraryName(String libraryName) {
    return new LocationBuilder(
      this.name,
      this.code,
      libraryName,
      this.institutionId,
      this.campusId,
      this.libraryId,
      this.primaryServicePointId,
      this.servingServicePointIds);
  }

  public LocationBuilder forInstitution(UUID institutionId) {
    return new LocationBuilder(
      this.name,
      this.code,
      this.libraryName,
      institutionId,
      this.campusId,
      this.libraryId,
      this.primaryServicePointId,
      this.servingServicePointIds);
  }

  public LocationBuilder forCampus(UUID campusId) {
    return new LocationBuilder(
      this.name,
      this.code,
      this.libraryName,
      this.institutionId,
      campusId,
      this.libraryId,
      this.primaryServicePointId,
      this.servingServicePointIds);
  }

  public LocationBuilder forLibrary(UUID libraryId) {
    return new LocationBuilder(
      this.name,
      this.code,
      this.libraryName,
      this.institutionId,
      this.campusId,
      libraryId,
      this.primaryServicePointId,
      this.servingServicePointIds);
  }

  public LocationBuilder withPrimaryServicePoint(UUID primaryServicePointId) {
    return new LocationBuilder(
      this.name,
      this.code,
      this.libraryName,
      this.institutionId,
      this.campusId,
      this.libraryId,
      primaryServicePointId,
      this.servingServicePointIds)
      .servedBy(primaryServicePointId);
  }

  public LocationBuilder servedBy(UUID servicePointId) {
    final HashSet<UUID> updatedServingServicePoints
      = new HashSet<>(servingServicePointIds);

    updatedServingServicePoints.add(servicePointId);

    return new LocationBuilder(
      this.name,
      this.code,
      this.libraryName,
      this.institutionId,
      this.campusId,
      this.libraryId,
      this.primaryServicePointId,
      updatedServingServicePoints);
  }
}
