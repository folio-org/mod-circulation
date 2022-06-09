package api.support.builders;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.UUID;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.With;

@With
@AllArgsConstructor
public class NoteTypeBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String typeName;
  public NoteTypeBuilder() {
    this(null, null);
  }
  @Override
  public JsonObject create() {
    JsonObject object = new JsonObject();
    write(object, "id", id);
    write(object, "name", typeName);
    return object;
  }
}
