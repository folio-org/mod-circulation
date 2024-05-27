package org.folio.circulation.domain;

import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.mapToList;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.representations.ItemSummaryRepresentation;
import org.folio.circulation.storage.mappers.ItemMapper;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

@Value
@ToString(onlyExplicitlyIncluded = true)
public class SearchInstance {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  JsonObject representation;
  String id;
  @NonNull Collection<Item> items;

  public static SearchInstance from(JsonObject representation) {
    return new SearchInstance(representation, representation.getString("id"), mapItems(representation));
  }

  private static List<Item> mapItems(JsonObject representation) {
    return mapToList(representation, "items", new ItemMapper()::toDomain);
  }

  public SearchInstance changeItems(Collection<Item> items) {
    JsonArray itemsArray = new JsonArray();
    for (Item item : items) {
      itemsArray.add(new ItemSummaryRepresentation().createItemSummary(item));
    }
    write(representation, "items", itemsArray);
    return new SearchInstance(representation, id, items);
  }

  public JsonObject toJson() {
    return representation;
  }
}
