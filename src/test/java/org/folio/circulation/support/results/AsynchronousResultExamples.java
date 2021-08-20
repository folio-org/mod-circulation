package org.folio.circulation.support.results;

import static org.folio.circulation.support.results.AsynchronousResult.failure;
import static org.folio.circulation.support.results.ResultExamples.exampleFailure;

class AsynchronousResultExamples {
  static AsynchronousResult<Integer> alreadyFailed() {
    return failure(exampleFailure("Already failed"));
  }
}
