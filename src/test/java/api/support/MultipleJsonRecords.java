package api.support;

import static api.support.JsonCollectionAssistant.getRecordById;
import static org.folio.circulation.support.StreamToListMapper.toList;
import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.toStream;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.folio.circulation.support.http.client.Response;

import io.vertx.core.json.JsonObject;

public class MultipleJsonRecords implements Iterable<JsonObject> {
  private final List<JsonObject> records;
  private final int totalRecords;

  public MultipleJsonRecords(List<JsonObject> records, int totalRecords) {
    this.records = records;
    this.totalRecords = totalRecords;
  }

  public static MultipleJsonRecords multipleRecordsFrom(Response response,
    String arrayPropertyName) {

    final JsonObject json = response.getJson();

    return new MultipleJsonRecords(toList(toStream(json, arrayPropertyName)),
      json.getInteger("totalRecords"));
  }

  public JsonObject getById(UUID id) {
    return getRecordById(records.stream(), id).orElse(null);
  }

  public Stream<JsonObject> stream() {
    return records.stream();
  }

  public int size() {
    return records.size();
  }

  public int totalRecords() {
    return totalRecords;
  }

  public JsonObject getFirst() {
    return records.size() > 0 ? records.get(0) : null;
  }

  @Override
  public Iterator<JsonObject> iterator() {
    return records.iterator();
  }

  @Override
  public void forEach(Consumer<? super JsonObject> action) {
    records.forEach(action);
  }
}
