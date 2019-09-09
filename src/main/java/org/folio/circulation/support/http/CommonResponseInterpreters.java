package org.folio.circulation.support.http;

import static org.folio.circulation.support.Result.of;

import org.folio.circulation.support.http.client.ResponseInterpreter;

public class CommonResponseInterpreters {
  private CommonResponseInterpreters() { }

  public static <T> ResponseInterpreter<T> noContentRecordInterpreter(T record) {
    return mapToRecordInterpreter(record, 204);
  }

  public static <T> ResponseInterpreter<T> mapToRecordInterpreter(T record, Integer... onStates) {
    ResponseInterpreter<T> interpreter = new ResponseInterpreter<>();

    for (Integer status : onStates) {
      interpreter = interpreter.on(status, of(() -> record));
    }

    return interpreter;
  }
}
