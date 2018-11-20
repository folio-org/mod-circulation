package api.support.builders;

import io.vertx.core.json.JsonObject;
import java.util.UUID;
import org.folio.circulation.support.http.client.IndividualResource;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;


public class PatronGroupBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String group;
  private final String desc;

  public PatronGroupBuilder(
    UUID id,
    String group,
    String desc) {
    this.id = id;
    this.group = group;
    this.desc = desc;
  }
  
  public PatronGroupBuilder(String group, String desc) {
    this(
      UUID.randomUUID(),
      group,
      desc);
  }
  
  public static PatronGroupBuilder from(IndividualResource response) {
    JsonObject representation = response.getJson();
    return new PatronGroupBuilder(
        UUID.fromString(representation.getString("id")),
        getProperty(representation, "group"),
        getProperty(representation, "desc")
    );
  }
  
  @Override
  public JsonObject create() {
    JsonObject patronGroup = new JsonObject();
    put(patronGroup, "id", this.id);
    put(patronGroup, "group", this.group);
    put(patronGroup, "desc", this.desc);
    
    return patronGroup;
  }
  
  public PatronGroupBuilder withId(UUID newId) {
    return new PatronGroupBuilder(
      newId,
      this.group,
      this.desc);
  }
  
  public PatronGroupBuilder withGroup(String newGroup) {
    return new PatronGroupBuilder(
      this.id,
      newGroup,
      this.desc);
  }
  
  public PatronGroupBuilder withDesc(String newDesc) {
    return new PatronGroupBuilder(
      this.id,
      this.group,
      newDesc);
  }
  
}
