package api.support.builders;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class InstanceBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String title;
  private final UUID instanceTypeId;
  private final JsonArray contributors;
  private final List<Pair<UUID, String>> identifiers;
  private final JsonArray publication;
  private final JsonArray editions;

  public InstanceBuilder(String title, UUID instanceTypeId) {
    this(UUID.randomUUID(), title, instanceTypeId);
  }

  public InstanceBuilder(UUID id, String title, UUID instanceTypeId) {
    this(id, title, new JsonArray(), instanceTypeId, Collections.emptyList(), new JsonArray(), new JsonArray());
  }

  private InstanceBuilder(UUID id,
    String title,
    JsonArray contributors,
    UUID instanceTypeId,
    List<Pair<UUID, String>> identifiers,
    JsonArray publication,
    JsonArray editions) {

    this.id = id;
    this.title = title;
    this.contributors = contributors;
    this.instanceTypeId = instanceTypeId;
    this.identifiers = identifiers;
    this.editions = editions;
    this.publication = publication;
  }

  @Override
  public JsonObject create() {
    final JsonObject instance = new JsonObject();

    put(instance, "id", id);
    put(instance, "_version", 6);
    put(instance, "title", title);
    put(instance, "source", "Local");
    put(instance, "instanceTypeId", instanceTypeId);
    put(instance, "contributors", contributors);
    put(instance, "identifiers", identifiersToJson());
    put(instance, "editions", editions);
    put(instance, "publication", publication);

    return instance;
  }

  private JsonArray identifiersToJson() {
    return new JsonArray(identifiers.stream()
      .map(pair -> new JsonObject()
        .put("identifierTypeId", pair.getKey().toString())
        .put("value", pair.getValue()))
      .collect(Collectors.toList()));
  }

  public InstanceBuilder withId(UUID id) {
    return new InstanceBuilder(
      id,
      this.title,
      this.contributors,
      this.instanceTypeId,
      this.identifiers,
      this.publication,
      this.editions);
  }

  public Builder withInstanceTypeId(UUID instanceTypeId) {
    return new InstanceBuilder(
      this.id,
      this.title,
      this.contributors,
      instanceTypeId,
      this.identifiers,
      this.publication,
      this.editions);
  }

  public InstanceBuilder withContributor(String name, UUID typeId) {
    return withContributor(name, typeId, false);
  }

  public InstanceBuilder withContributor(String name, UUID typeId, Boolean primary) {
    final JsonObject newContributor = new JsonObject();

    write(newContributor, "name", name);
    write(newContributor, "contributorNameTypeId", typeId);
    write(newContributor, "primary", primary);

    return withContributors(this.contributors.copy().add(newContributor));
  }

  private InstanceBuilder withContributors(JsonArray contributors) {
    return new InstanceBuilder(
      this.id,
      this.title,
      contributors,
      this.instanceTypeId,
      this.identifiers,
      this.publication,
      this.editions);
  }

  public InstanceBuilder withSinglePublication(String publisher, String place, String dateOfPublication) {
    final JsonObject singlePublication = new JsonObject();
    write(singlePublication, "publisher", publisher);
    write(singlePublication, "place", place);
    write(singlePublication, "dateOfPublication", dateOfPublication);
    return withPublication(this.publication.copy().add(singlePublication));
  }

  private InstanceBuilder withPublication(JsonArray publication) {
    return new InstanceBuilder(
      this.id,
      this.title,
      contributors,
      this.instanceTypeId,
      this.identifiers,
      publication,
      this.editions);
  }

  public InstanceBuilder withSingleEdition(String edition) {
    return withEditions(this.editions.copy().add(edition));
  }

  private InstanceBuilder withEditions(JsonArray editions) {
    return new InstanceBuilder(
      this.id,
      this.title,
      contributors,
      this.instanceTypeId,
      this.identifiers,
      this.publication,
      editions);
  }

  public InstanceBuilder addIdentifier(UUID typeId, String value) {
    List<Pair<UUID, String>> identifiersCopy = new ArrayList<>(identifiers);
    identifiersCopy.add(new ImmutablePair<>(typeId, value));

    return new InstanceBuilder(
      this.id,
      this.title,
      this.contributors,
      this.instanceTypeId,
      identifiersCopy,
      this.publication,
      this.editions);
  }
}
