package org.folio.circulation.resources.handlers.error;

import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_STATUS;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.Assert.*;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.Result;
import org.junit.Test;

public class FailFastErrorHandlerTest {
  private static final HttpFailure TEST_ERROR = new ServerErrorFailure("test error");
  private static final Result<Object> FAILED_RESULT = failed(TEST_ERROR);
  private static final Result<Object> SUCCEEDED_RESULT = succeeded(new Object());

  private final FailFastErrorHandler handler = new FailFastErrorHandler();

  @Test
  public void handleErrorReturnsFailedResultWithError() {
    Result<Object> output = handler.handleError(TEST_ERROR, INVALID_STATUS, SUCCEEDED_RESULT);

    assertTrue(output.failed());
    assertSame(TEST_ERROR, output.cause());
  }

  @Test
  public void handleErrorHandlesNullsCorrectly() {
    Result<Object> output = handler.handleError(null, null, null);

    assertTrue(output.failed());
    assertNull(output.cause());
  }

  @Test
  public void handledResultSucceeded() {
    Result<Object> input = succeeded(new Object());
    Result<Object> output = handler.handleResult(input, INVALID_STATUS, SUCCEEDED_RESULT);

    assertSame(input, output);
  }

  @Test
  public void handleResultFailed() {
    Result<Object> output = handler.handleResult(FAILED_RESULT, INVALID_STATUS, SUCCEEDED_RESULT);

    assertSame(FAILED_RESULT, output);
  }

  @Test
  public void handleResultHandlesNullsCorrectly() {
    Result<Object> output = handler.handleResult(null, null, null);

    assertNull(output);
  }

  @Test
  public void handleResultHandlesNullsWithinResultsCorrectly() {
    Result<Object> input = failed(null);
    Result<Object> output = handler.handleResult(input, null, succeeded(null));

    assertSame(input, output);
  }

  @Test
  public void handleValidationErrorReturnsFailedResultWithError() {
    Result<Object> output = handler.handleValidationError(TEST_ERROR, INVALID_STATUS, SUCCEEDED_RESULT);

    assertTrue(output.failed());
    assertSame(TEST_ERROR, output.cause());
  }

  @Test
  public void handleValidationErrorHandlesNullsCorrectly() {
    Result<Object> output = handler.handleValidationError(null, null, null);

    assertTrue(output.failed());
    assertNull(output.cause());
  }

  @Test
  public void handledValidationResultSucceeded() {
    Result<Object> input = succeeded(new Object());
    Result<Object> output = handler.handleValidationResult(input, INVALID_STATUS, SUCCEEDED_RESULT);

    assertSame(input, output);
  }

  @Test
  public void handleValidationResultFailed() {
    Result<Object> input = failed(TEST_ERROR);
    Result<Object> output = handler.handleValidationResult(input, INVALID_STATUS, SUCCEEDED_RESULT);

    assertSame(input, output);
  }

  @Test
  public void handleValidationResultHandlesNullsCorrectly() {
    Result<Object> output = handler.handleValidationResult(null, null, null);

    assertNull(output);
  }

  @Test
  public void handleValidationResultHandlesNullsWithinResultsCorrectly() {
    Result<Object> input = failed(null);
    Result<Object> output = handler.handleValidationResult(input, null, succeeded(null));

    assertSame(input, output);
  }

  @Test
  public void errorMapRemainsEmpty() {
    callAllHandlerMethods();

    assertTrue(handler.getErrors().isEmpty());
  }

  @Test
  public void hasAnyAlwaysReturnsFalse() {
    callAllHandlerMethods();

    assertFalse(handler.hasAny(INVALID_STATUS));
  }

  @Test
  public void failIfHasErrorsAlwaysReturnsSucceededResult() {
    callAllHandlerMethods();
    Object input = new Object();
    Result<Object> output = handler.failIfHasErrors(input);

    assertTrue(output.succeeded());
    assertSame(input, output.value());
  }

  private void callAllHandlerMethods() {
    handler.handleResult(failed(TEST_ERROR), INVALID_STATUS, SUCCEEDED_RESULT);
    handler.handleError(TEST_ERROR, INVALID_STATUS, SUCCEEDED_RESULT);
    handler.handleValidationResult(failed(TEST_ERROR), INVALID_STATUS, SUCCEEDED_RESULT);
    handler.handleValidationError(TEST_ERROR, INVALID_STATUS, SUCCEEDED_RESULT);
  }

  @Test(expected = UnsupportedOperationException.class)
  public void attemptToAddErrorToMapThrowsException() {
    handler.getErrors().put(TEST_ERROR, INVALID_STATUS);
  }
}