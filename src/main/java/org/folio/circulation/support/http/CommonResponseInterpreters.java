package org.folio.circulation.support.http;

import static org.folio.circulation.support.Result.of;

import org.folio.circulation.support.http.client.ResponseInterpreter;

public class CommonResponseInterpreters {
  private CommonResponseInterpreters() { }

  public static <T> ResponseInterpreter<T> noContentRecordInterpreter(T record) {
    return new ResponseInterpreter<T>().on(204, of(() -> record));
  }
}
