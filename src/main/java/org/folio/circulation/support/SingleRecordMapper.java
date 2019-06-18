package org.folio.circulation.support;

import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;

public class SingleRecordMapper<T> {
  private final ResponseInterpreter<T> interpreter;

  SingleRecordMapper(ResponseInterpreter<T> interpreter) {
    this.interpreter = interpreter;
  }

  Result<T> mapFrom(Response response) {
    return interpreter.apply(response);
  }
}
