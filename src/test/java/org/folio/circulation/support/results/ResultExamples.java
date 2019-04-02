package org.folio.circulation.support.results;

import static org.folio.circulation.support.Result.failed;

import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ResponseWritableResult;

class ResultExamples {
  static ResponseWritableResult<Integer> alreadyFailed() {
    return failed(exampleFailure("Already failed"));
  }

  static ResponseWritableResult<Boolean> conditionFailed() {
    return failed(exampleFailure("Condition failed"));
  }

  static <T> ResponseWritableResult<T> actionFailed() {
    return failed(exampleFailure("Action failed"));
  }

  static Result<Integer> throwOnExecution() {
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
