package org.folio.circulation.resources.handlers.error;

import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_ITEM_ID;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

class FailFastErrorHandlerTest {
  private static final HttpFailure TEST_ERROR = new ServerErrorFailure("test error");
  private static final Result<String> FAILED_RESULT = failed(TEST_ERROR);
  private static final Result<String> SUCCEEDED_RESULT = succeeded("success");

  private final FailFastErrorHandler handler = new FailFastErrorHandler();

  @Test
  void handleAnyErrorReturnsFailedResultWithError() {
    Result<String> output = handler.handleAnyError(TEST_ERROR, INVALID_ITEM_ID, SUCCEEDED_RESULT);

    assertTrue(output.failed());
    assertSame(TEST_ERROR, output.cause());
  }

  @Test
  void handleAnyErrorHandlesNullsCorrectly() {
    Result<String> output = handler.handleAnyError(null, null, null);

    assertTrue(output.failed());
    assertNull(output.cause());
  }

  @Test
  void handledAnyResultSucceeded() {
    Result<String> input = succeeded("input result");
    Result<String> output = handler.handleAnyResult(input, INVALID_ITEM_ID, SUCCEEDED_RESULT);

    assertSame(input, output);
  }

  @Test
  void handleAnyResultFailed() {
    Result<String> output = handler.handleAnyResult(FAILED_RESULT, INVALID_ITEM_ID, SUCCEEDED_RESULT);

    assertSame(FAILED_RESULT, output);
  }

  @Test
  void handleAnyResultHandlesNullsCorrectly() {
    Result<String> output = handler.handleAnyResult(null, null, null);

    assertNull(output);
  }

  @Test
  void handleAnyResultHandlesNullsWithinResultsCorrectly() {
    Result<String> input = failed(null);
    Result<String> output = handler.handleAnyResult(input, null, succeeded(null));

    assertSame(input, output);
  }

  @Test
  void handleValidationErrorReturnsFailedResultWithError() {
    Result<String> output = handler.handleValidationError(TEST_ERROR, INVALID_ITEM_ID, SUCCEEDED_RESULT);

    assertTrue(output.failed());
    assertSame(TEST_ERROR, output.cause());
  }

  @Test
  void handleValidationErrorHandlesNullsCorrectly() {
    Result<String> output = handler.handleValidationError(null, null, null);

    assertTrue(output.failed());
    assertNull(output.cause());
  }

  @Test
  void handledValidationResultSucceeded() {
    Result<String> input = succeeded("input result");
    Result<String> output = handler.handleValidationResult(input, INVALID_ITEM_ID, SUCCEEDED_RESULT);

    assertSame(input, output);
  }

  @Test
  void handleValidationResultFailed() {
    Result<String> input = failed(TEST_ERROR);
    Result<String> output = handler.handleValidationResult(input, INVALID_ITEM_ID, SUCCEEDED_RESULT);

    assertSame(input, output);
  }

  @Test
  void handleValidationResultHandlesNullsCorrectly() {
    Result<String> output = handler.handleValidationResult(null, null, (String) null);

    assertNull(output);
  }

  @Test
  void handleValidationResultHandlesNullsWithinResultsCorrectly() {
    Result<String> input = failed(null);
    Result<String> output = handler.handleValidationResult(input, null, succeeded(null));

    assertSame(input, output);
  }

  @Test
  void errorMapRemainsEmpty() {
    callAllHandlerMethods();

    assertTrue(handler.getErrors().isEmpty());
  }

  @Test
  void hasAnyAlwaysReturnsFalse() {
    callAllHandlerMethods();

    assertFalse(handler.hasAny(INVALID_ITEM_ID));
  }

  @Test
  void hasNoneAlwaysReturnsTrue() {
    callAllHandlerMethods();

    assertTrue(handler.hasNone(INVALID_ITEM_ID));
  }

  @Test
  void failWithValidationErrorsIfHasAnyAlwaysReturnsSucceededResult() {
    callAllHandlerMethods();
    String otherwise = "otherwise";
    Result<String> output = handler.failWithValidationErrors(otherwise);

    assertTrue(output.succeeded());
    assertSame(otherwise, output.value());
  }

  private void callAllHandlerMethods() {
    handler.handleAnyResult(failed(TEST_ERROR), INVALID_ITEM_ID, SUCCEEDED_RESULT);
    handler.handleAnyError(TEST_ERROR, INVALID_ITEM_ID, SUCCEEDED_RESULT);
    handler.handleValidationResult(FAILED_RESULT, INVALID_ITEM_ID, SUCCEEDED_RESULT);
    handler.handleValidationError(TEST_ERROR, INVALID_ITEM_ID, SUCCEEDED_RESULT);
  }

  @Test
  void attemptToAddErrorToMapThrowsException() {
    assertThrows(UnsupportedOperationException.class, () -> {
      handler.getErrors().put(TEST_ERROR, INVALID_ITEM_ID);
    });
  }
}
