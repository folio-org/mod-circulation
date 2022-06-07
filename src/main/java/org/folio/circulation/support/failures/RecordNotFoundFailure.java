package org.folio.circulation.support.failures;

import org.folio.circulation.support.http.server.response.ClientErrorResponse;

import io.vertx.core.http.HttpServerResponse;

public class RecordNotFoundFailure implements HttpFailure {
  private final String recordType;
  private final String id;

  public RecordNotFoundFailure(String recordType, String id) {
    this.recordType = recordType;
    this.id = id;
  }

  public String getRecordType() {
    return recordType;
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    ClientErrorResponse.notFound(response, toString());
  }

  @Override
  public String toString() {
    return String.format("%s record with ID \"%s\" cannot be found",
      recordType, id);
  }
}
