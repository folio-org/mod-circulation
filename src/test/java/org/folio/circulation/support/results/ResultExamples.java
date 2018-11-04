package org.folio.circulation.support.results;

import static org.folio.circulation.support.HttpResult.failed;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.WritableHttpResult;

class ResultExamples {
  static WritableHttpResult<Integer> alreadyFailed() {
    return failed(exampleFailure("Already failed"));
  }

  static WritableHttpResult<Boolean> conditionFailed() {
    return failed(exampleFailure("Condition failed"));
  }

  static HttpResult<Integer> shouldNotExecute() {
    throw exampleException("Should not execute");
  }

  static RuntimeException exampleException(String message) {
    return new RuntimeException(message);
  }

  private static ServerErrorFailure exampleFailure(String message) {
    return new ServerErrorFailure(exampleException(message));
  }

  static RuntimeException somethingWentWrong() {
    return exampleException("Something went wrong");
  }
}
