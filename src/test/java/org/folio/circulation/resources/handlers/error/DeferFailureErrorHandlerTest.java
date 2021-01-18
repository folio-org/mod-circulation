package org.folio.circulation.resources.handlers.error;

import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_ITEM_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_STATUS;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.List;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.MultiErrorFailure;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;
import org.junit.Test;

public class DeferFailureErrorHandlerTest {

  private static final HttpFailure SERVER_ERROR = new ServerErrorFailure("server error");
  private static final HttpFailure VALIDATION_ERROR = new ValidationErrorFailure(
    List.of(new ValidationError("validation failed", "key", "value")));
  private static final Result<Object> SERVER_ERROR_RESULT = failed(SERVER_ERROR);
  private static final Result<Object> VALIDATION_ERROR_RESULT = failed(VALIDATION_ERROR);
  private static final Result<Object> SUCCEEDED_RESULT = succeeded(new Object());

  private final DeferFailureErrorHandler handler = new DeferFailureErrorHandler();

  @Test
  public void handleErrorAddsErrorToMapAndReturnsPassedValue() {
    Result<Object> output = handler.handleError(SERVER_ERROR, INVALID_STATUS, SUCCEEDED_RESULT);

    assertSame(SUCCEEDED_RESULT, output);
    assertEquals(1, handler.getErrors().size());
    assertTrue(handler.getErrors().containsKey(SERVER_ERROR));
    assertSame(INVALID_STATUS, handler.getErrors().get(SERVER_ERROR));
  }

  @Test
  public void handleErrorIgnoresNullError() {
    Result<Object> output = handler.handleError(null, INVALID_STATUS, SUCCEEDED_RESULT);

    assertSame(SUCCEEDED_RESULT, output);
    assertTrue(handler.getErrors().isEmpty());
  }

  @Test
  public void handleErrorHandlesNullErrorTypeCorrectly() {
    Result<Object> output = handler.handleError(SERVER_ERROR, null, SUCCEEDED_RESULT);

    assertSame(SUCCEEDED_RESULT, output);
    assertTrue(handler.getErrors().containsKey(SERVER_ERROR));
    assertNull(handler.getErrors().get(SERVER_ERROR));
  }

  @Test
  public void handleErrorHandlesNullReturnValueCorrectly() {
    Result<Object> output = handler.handleError(SERVER_ERROR, INVALID_STATUS, null);

    assertTrue(handler.getErrors().containsKey(SERVER_ERROR));
    assertNull(output);
  }

  @Test
  public void handledResultIgnoresErrorWhenInputResultIsSucceeded() {
    Result<Object> input = succeeded(new Object());
    Result<Object> output = handler.handleResult(input, INVALID_STATUS, SUCCEEDED_RESULT);

    assertTrue(output.succeeded());
    assertSame(input.value(), output.value());
    assertTrue(handler.getErrors().isEmpty());
  }

  @Test
  public void handleResultHandlesFailedResultCorrectly() {
    Result<Object> output = handler.handleResult(
      SERVER_ERROR_RESULT, INVALID_STATUS, SUCCEEDED_RESULT);

    assertSame(SUCCEEDED_RESULT, output);
    assertTrue(handler.getErrors().containsKey(SERVER_ERROR));
    assertSame(INVALID_STATUS, handler.getErrors().get(SERVER_ERROR));
  }

  @Test
  public void handleResultIgnoresNullErrorWithinInputResult() {
    Result<Object> output = handler.handleResult(failed(null), INVALID_STATUS, SUCCEEDED_RESULT);

    assertSame(SUCCEEDED_RESULT, output);
    assertTrue(handler.getErrors().isEmpty());
  }

  @Test
  public void handleResultHandlesNullReturnValueCorrectly() {
    Result<Object> output = handler.handleResult(SERVER_ERROR_RESULT, INVALID_STATUS, null);

    assertNull(output);
    assertTrue(handler.getErrors().containsKey(SERVER_ERROR));
  }

  @Test
  public void handleValidationError() {
    Result<Object> output = handler.handleValidationError(
      VALIDATION_ERROR, INVALID_STATUS, SUCCEEDED_RESULT);

    assertSame(SUCCEEDED_RESULT, output);
    assertTrue(handler.getErrors().containsKey(VALIDATION_ERROR));
    assertSame(INVALID_STATUS, handler.getErrors().get(VALIDATION_ERROR));
  }

  @Test
  public void handleValidationErrorIgnoresNonValidationError() {
    Result<Object> output = handler.handleValidationError(
      SERVER_ERROR, INVALID_STATUS, SUCCEEDED_RESULT);

    assertTrue(output.failed());
    assertSame(SERVER_ERROR, output.cause());
    assertTrue(handler.getErrors().isEmpty());
  }

  @Test
  public void handleValidationErrorIgnoresNullError() {
    Result<Object> output = handler.handleValidationError(null, INVALID_STATUS, SUCCEEDED_RESULT);

    assertTrue(output.failed());
    assertNull(output.cause());
    assertTrue(handler.getErrors().isEmpty());
  }

  @Test
  public void handledValidationResult() {
    Result<Object> output = handler.handleValidationResult(
      VALIDATION_ERROR_RESULT, INVALID_STATUS, SUCCEEDED_RESULT);

    assertSame(SUCCEEDED_RESULT, output);
    assertTrue(handler.getErrors().containsKey(VALIDATION_ERROR));
    assertSame(INVALID_STATUS, handler.getErrors().get(VALIDATION_ERROR));
  }

  @Test
  public void handledValidationResultIgnoresNonValidationError() {
    Result<Object> output = handler.handleValidationResult(
      SERVER_ERROR_RESULT, INVALID_STATUS, SUCCEEDED_RESULT);

    assertTrue(output.failed());
    assertSame(SERVER_ERROR, output.cause());
    assertTrue(handler.getErrors().isEmpty());
  }

  @Test
  public void handlerAccumulatesErrors() {
    handler.handleResult(SERVER_ERROR_RESULT, INVALID_STATUS, SUCCEEDED_RESULT);
    handler.handleValidationResult(VALIDATION_ERROR_RESULT, INVALID_STATUS, SUCCEEDED_RESULT);

    assertEquals(2, handler.getErrors().size());
    assertTrue(handler.getErrors().keySet().containsAll(List.of(SERVER_ERROR, VALIDATION_ERROR)));
  }

  @Test
  public void failIfHasErrors() {
    handler.handleResult(SERVER_ERROR_RESULT, INVALID_STATUS, SUCCEEDED_RESULT);
    Result<Object> output = handler.failIfHasErrors(new Object());

    assertTrue(output.failed());
    assertTrue(output.cause() instanceof MultiErrorFailure);

    Collection<HttpFailure> errors = ((MultiErrorFailure) output.cause()).getErrors();

    assertEquals(1, errors.size());
    assertSame(SERVER_ERROR, errors.iterator().next());
  }

  @Test
  public void failIfHasErrorsReturnsPassedValueWhenHasNoErrors() {
    Object otherwise = new Object();
    Result<Object> output = handler.failIfHasErrors(otherwise);

    assertTrue(output.succeeded());
    assertSame(otherwise, output.value());
  }

  @Test
  public void hasAnyReturnsTrueWhenContainsErrorOfPassedType() {
    handler.handleResult(SERVER_ERROR_RESULT, INVALID_STATUS, SUCCEEDED_RESULT);

    assertTrue(handler.hasAny(INVALID_STATUS, INVALID_ITEM_ID));
  }

  @Test
  public void hasAnyReturnsFalseWhenContainsNoErrorsOfPassedType() {
    handler.handleResult(SERVER_ERROR_RESULT, INVALID_STATUS, SUCCEEDED_RESULT);

    assertFalse(handler.hasAny(INVALID_ITEM_ID));
  }

  @Test
  public void hasAnyReturnsFalseWhenContainsNoErrors() {
    assertFalse(handler.hasAny(INVALID_STATUS));
  }

}