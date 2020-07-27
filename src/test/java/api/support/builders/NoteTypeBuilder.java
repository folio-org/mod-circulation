package api.support.builders;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class NoteTypeBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String typeName;

  public NoteTypeBuilder() {
    this.id = null;
    this.typeName = null;
  }

  public NoteTypeBuilder(UUID id, String typeName) {
    this.id = id;
    this.typeName = typeName;
  }

  @Override
  public JsonObject create() {
    JsonObject object = new JsonObject();

    write(object, "id", id);
    write(object, "typeName", typeName);

    return object;
  }

  public NoteTypeBuilder withId(UUID id) {
    return new NoteTypeBuilder(id, typeName);
  }

  public NoteTypeBuilder withTypeName(String typeName) {
    return new NoteTypeBuilder(id, typeName);
  }
}
