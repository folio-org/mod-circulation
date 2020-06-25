package api.support.jackson.serializer;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import io.vertx.core.json.JsonObject;

/**
 * Jackson serializer that allows vertx JsonObject to be correctly serialized.
 */
public final class JsonObjectJacksonSerializer extends JsonSerializer<JsonObject> {
  @Override
  public void serialize(JsonObject value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
    gen.writeRaw(value.encode());
  }
}
