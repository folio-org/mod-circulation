package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.MultipleRecordsWrapper;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.HttpResult.succeeded;
import static org.folio.circulation.support.MultipleRecordsWrapper.fromBody;

public class MultipleRecords<T> {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Collection<T> records;
  private final Integer totalRecords;

  public static <T> MultipleRecords<T> empty() {
    return new MultipleRecords<>(new ArrayList<>(), 0);
  }

  public MultipleRecords(Collection<T> records, Integer totalRecords) {
    this.records = records;
    this.totalRecords = totalRecords;
  }

  public static <T> HttpResult<MultipleRecords<T>> from(
    Response response,
    Function<JsonObject, T> mapper, String recordsPropertyName) {
    if(response != null) {
      log.info("Response received, status code: {} body: {}",
        response.getStatusCode(), response.getBody());

      if (response.getStatusCode() != 200) {
        return failed(new ServerErrorFailure(
          String.format("Failed to fetch %s from storage (%s:%s)",
            recordsPropertyName, response.getStatusCode(), response.getBody())));
      }

      final MultipleRecordsWrapper wrappedRecords = fromBody(response.getBody(),
        recordsPropertyName);

      if (wrappedRecords.isEmpty()) {
        return succeeded(empty());
      }

      final MultipleRecords<T> mapped = new MultipleRecords<>(
        wrappedRecords.getRecords()
          .stream()
          .map(mapper)
          .collect(Collectors.toList()),
        wrappedRecords.getTotalRecords());

      return succeeded(mapped);
    }
    else {
      log.warn("Did not receive response to request");
      return failed(new ServerErrorFailure(
        String.format("Did not receive response to request for multiple %s",
          recordsPropertyName)));
    }
  }

  //TODO: Maybe skip the wrapper and go straight to JSON?
  public MultipleRecordsWrapper mapToRepresentations(
    Function<T, JsonObject> mapper,
    String recordsPropertyName) {

    final List<JsonObject> mappedRequests = getRecords().stream()
      .map(mapper)
      .collect(Collectors.toList());

    return new MultipleRecordsWrapper(mappedRequests,
      recordsPropertyName, getTotalRecords());
  }

  public Collection<T> getRecords() {
    return records;
  }

  public Integer getTotalRecords() {
    return totalRecords;
  }
}
