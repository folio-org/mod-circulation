package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.representations.ItemSummaryRepresentation;
import org.folio.circulation.storage.mappers.ItemMapper;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.mapToList;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class InstanceExtended {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  JsonObject representation;

  @ToString.Include
  String id;
  @ToString.Include
  String title;

  @NonNull Collection<Item> items;

  public static InstanceExtended from(JsonObject representation) {
    return new InstanceExtended(representation, representation.getString("id"),
      representation.getString("title"), mapItems(representation));
  }

  private static List<Item> mapItems(JsonObject representation) {
    return mapToList(representation, "items", new ItemMapper()::toDomain);
  }

  public InstanceExtended changeItems(Collection<Item> items) {
    JsonArray itemsArray = new JsonArray();
    for (Item item : items) {
      itemsArray.add(new ItemSummaryRepresentation().createItemSummary(item));
    }
    write(representation, "items", itemsArray);
    return new InstanceExtended(representation, id, title, items);
  }

  public JsonObject toJson() {
    return representation;
  }
}
