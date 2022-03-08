package org.folio.circulation.infrastructure.storage;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.folio.circulation.domain.MultipleRecords;

import io.vertx.core.json.JsonObject;
import lombok.NonNull;

/*
  Is specific to JsonObject because a copy is required due to JsonObject being
  mutable (and hence other reference could change the entry)

  Could be made generic by introducing an optional mapper for values (which would
  do the copy for JsonObject, could define to Function.identity())
 */
public class IdentityMap {
  private final Map<String, JsonObject> map = new HashMap<>();
  private final Function<JsonObject, String> keyMapper;

  public IdentityMap(Function<JsonObject, String> keyMapper) {
    this.keyMapper = keyMapper;
  }

  public boolean entryNotPresent(String key) {
    return !map.containsKey(key);
  }

  public JsonObject get(String key) {
    return map.get(key);
  }

  public JsonObject add(JsonObject value) {
    if (value != null) {
      // Needs to be a copy because JsonObject is mutable
      map.put(keyMapper.apply(value), value.copy());
    }

    return value;
  }

  public MultipleRecords<JsonObject> add(@NonNull MultipleRecords<JsonObject> values) {
    add(values.getRecords());

    return values;
  }

  public Collection<JsonObject> add(@NonNull Collection<JsonObject> values) {
    values.forEach(this::add);

    return values;
  }
}
