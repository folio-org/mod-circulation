package api.support;

import static api.support.JsonCollectionAssistant.getRecordById;
import static org.folio.circulation.support.JsonArrayHelper.mapToList;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.folio.circulation.support.http.client.Response;

import io.vertx.core.json.JsonObject;

public class MultipleJsonRecords {
  private final List<JsonObject> records;

  public MultipleJsonRecords(List<JsonObject> records) {
    this.records = records;
  }

  public static MultipleJsonRecords multipleRecordsFrom(Response response,
    String arrayPropertyName) {

    return new MultipleJsonRecords(mapToList(response.getJson(),
      arrayPropertyName));
  }

  public JsonObject getById(UUID id) {
    return getRecordById(records.stream(), id).orElse(null);
  }

  public Stream<JsonObject> stream() {
    return records.stream();
  }

  public void forEach(Consumer<JsonObject> consumer) {
    records.forEach(consumer);
  }

  public int size() {
    return records.size();
  }
}
