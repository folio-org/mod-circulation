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

  static <T> WritableHttpResult<T> actionFailed() {
    return failed(exampleFailure("Action failed"));
  }

  static HttpResult<Integer> throwOnExecution() {
    throw shouldNotExecute();
  }

  static RuntimeException exampleException(String message) {
    return new RuntimeException(message);
  }

  static ServerErrorFailure exampleFailure(String message) {
    return new ServerErrorFailure(exampleException(message));
  }

  static RuntimeException shouldNotExecute() {
    return exampleException("Should not execute");
  }

  static RuntimeException somethingWentWrong() {
    return exampleException("Something went wrong");
  }
}
