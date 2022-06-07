package org.folio.circulation.resources.handlers.error;

import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_ITEM_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_DOES_NOT_EXIST;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_INACTIVE;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;

import org.folio.circulation.support.failures.HttpFailure;
import org.folio.circulation.support.failures.ServerErrorFailure;
import org.folio.circulation.support.failures.ValidationErrorFailure;
import org.folio.circulation.support.http.server.error.ValidationError;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

class DeferFailureErrorHandlerTest {
  private static final HttpFailure SERVER_ERROR = new ServerErrorFailure("server error");
  private static final HttpFailure VALIDATION_ERROR = new ValidationErrorFailure(
    List.of(new ValidationError("validation failed", "key", "value")));
  private static final Result<String> SERVER_ERROR_RESULT = failed(SERVER_ERROR);
  private static final Result<String> VALIDATION_ERROR_RESULT = failed(VALIDATION_ERROR);
  private static final Result<String> SUCCEEDED_RESULT = succeeded("success");

  private final DeferFailureErrorHandler handler = new DeferFailureErrorHandler();

  @Test
  void handleAnyErrorAddsErrorToMapAndReturnsPassedValue() {
    Result<String> output = handler.handleAnyError(SERVER_ERROR, INVALID_ITEM_ID, SUCCEEDED_RESULT);

    assertSame(SUCCEEDED_RESULT, output);
    assertEquals(1, handler.getErrors().size());
    assertTrue(handler.getErrors().containsKey(SERVER_ERROR));
    assertSame(INVALID_ITEM_ID, handler.getErrors().get(SERVER_ERROR));
  }

  @Test
  void handleAnyErrorIgnoresNullError() {
    Result<String> output = handler.handleAnyError(null, INVALID_ITEM_ID, SUCCEEDED_RESULT);

    assertSame(SUCCEEDED_RESULT, output);
    assertTrue(handler.getErrors().isEmpty());
  }

  @Test
  void handleAnyErrorHandlesNullErrorTypeCorrectly() {
    Result<String> output = handler.handleAnyError(SERVER_ERROR, null, SUCCEEDED_RESULT);

    assertSame(SUCCEEDED_RESULT, output);
    assertTrue(handler.getErrors().containsKey(SERVER_ERROR));
    assertNull(handler.getErrors().get(SERVER_ERROR));
  }

  @Test
  void handleAnyErrorHandlesNullReturnValueCorrectly() {
    Result<String> output = handler.handleAnyError(SERVER_ERROR, INVALID_ITEM_ID, null);

    assertTrue(handler.getErrors().containsKey(SERVER_ERROR));
    assertNull(output);
  }

  @Test
  void handledAnyResultIgnoresErrorWhenInputResultIsSucceeded() {
    Result<String> input = succeeded("input result");
    Result<String> output = handler.handleAnyResult(input, INVALID_ITEM_ID, SUCCEEDED_RESULT);

    assertTrue(output.succeeded());
    assertSame(input.value(), output.value());
    assertTrue(handler.getErrors().isEmpty());
  }

  @Test
  void handleAnyResultHandlesFailedResultCorrectly() {
    Result<String> output = handler.handleAnyResult(
      SERVER_ERROR_RESULT, INVALID_ITEM_ID, SUCCEEDED_RESULT);

    assertSame(SUCCEEDED_RESULT, output);
    assertTrue(handler.getErrors().containsKey(SERVER_ERROR));
    assertSame(INVALID_ITEM_ID, handler.getErrors().get(SERVER_ERROR));
  }

  @Test
  void handleAnyResultIgnoresNullErrorWithinInputResult() {
    Result<String> output = handler.handleAnyResult(failed(null), INVALID_ITEM_ID, SUCCEEDED_RESULT);

    assertSame(SUCCEEDED_RESULT, output);
    assertTrue(handler.getErrors().isEmpty());
  }

  @Test
  void handleAnyResultHandlesNullReturnValueCorrectly() {
    Result<String> output = handler.handleAnyResult(SERVER_ERROR_RESULT, INVALID_ITEM_ID, null);

    assertNull(output);
    assertTrue(handler.getErrors().containsKey(SERVER_ERROR));
  }

  @Test
  void handleValidationError() {
    Result<String> output = handler.handleValidationError(
      VALIDATION_ERROR, INVALID_ITEM_ID, SUCCEEDED_RESULT);

    assertSame(SUCCEEDED_RESULT, output);
    assertTrue(handler.getErrors().containsKey(VALIDATION_ERROR));
    assertSame(INVALID_ITEM_ID, handler.getErrors().get(VALIDATION_ERROR));
  }

  @Test
  void handleValidationErrorIgnoresNonValidationError() {
    Result<String> output = handler.handleValidationError(
      SERVER_ERROR, INVALID_ITEM_ID, SUCCEEDED_RESULT);

    assertTrue(output.failed());
    assertSame(SERVER_ERROR, output.cause());
    assertTrue(handler.getErrors().isEmpty());
  }

  @Test
  void handleValidationErrorIgnoresNullError() {
    Result<String> output = handler.handleValidationError(null, INVALID_ITEM_ID, SUCCEEDED_RESULT);

    assertTrue(output.failed());
    assertNull(output.cause());
    assertTrue(handler.getErrors().isEmpty());
  }

  @Test
  void handledValidationResult() {
    Result<String> output = handler.handleValidationResult(
      VALIDATION_ERROR_RESULT, INVALID_ITEM_ID, SUCCEEDED_RESULT);

    assertSame(SUCCEEDED_RESULT, output);
    assertTrue(handler.getErrors().containsKey(VALIDATION_ERROR));
    assertSame(INVALID_ITEM_ID, handler.getErrors().get(VALIDATION_ERROR));
  }

  @Test
  void handledValidationResultIgnoresNonValidationError() {
    Result<String> output = handler.handleValidationResult(
      SERVER_ERROR_RESULT, INVALID_ITEM_ID, SUCCEEDED_RESULT);

    assertTrue(output.failed());
    assertSame(SERVER_ERROR, output.cause());
    assertTrue(handler.getErrors().isEmpty());
  }

  @Test
  void handlerAccumulatesErrors() {
    handler.handleAnyResult(SERVER_ERROR_RESULT, INVALID_ITEM_ID, SUCCEEDED_RESULT);
    handler.handleValidationResult(VALIDATION_ERROR_RESULT, INVALID_ITEM_ID, SUCCEEDED_RESULT);

    assertEquals(2, handler.getErrors().size());
    assertTrue(handler.getErrors().keySet().containsAll(List.of(SERVER_ERROR, VALIDATION_ERROR)));
  }

  @Test
  void failWithValidationErrorsReturnsValidationErrorsOnly() {
    ValidationError firstValidationError = new ValidationError("error1", "key1", "value1");
    ValidationErrorFailure firstValidationFailure = new ValidationErrorFailure(
      List.of(firstValidationError));

    ValidationError secondValidationError = new ValidationError("error2", "key2", "value2");
    ValidationErrorFailure secondValidationFailure = new ValidationErrorFailure(
      List.of(secondValidationError));

    handler.handleValidationError(firstValidationFailure, INVALID_ITEM_ID, SUCCEEDED_RESULT);
    handler.handleAnyError(SERVER_ERROR, ITEM_DOES_NOT_EXIST, SUCCEEDED_RESULT);
    handler.handleValidationError(secondValidationFailure, USER_IS_INACTIVE, SUCCEEDED_RESULT);

    Result<String> output = handler.failWithValidationErrors("should not return this string");

    assertTrue(output.failed());
    assertTrue(output.cause() instanceof ValidationErrorFailure);

    Collection<ValidationError> errors = ((ValidationErrorFailure) output.cause()).getErrors();

    assertEquals(2, errors.size());
    assertTrue(errors.contains(firstValidationError));
    assertTrue(errors.contains(secondValidationError));
  }

  @Test
  void failWithValidationErrorsReturnsPassedValueWhenHasNoValidationErrors() {
    handler.handleAnyError(SERVER_ERROR, ITEM_DOES_NOT_EXIST, SUCCEEDED_RESULT);
    assertEquals(1, handler.getErrors().size());
    assertTrue(handler.getErrors().containsKey(SERVER_ERROR));

    String otherwise = "should return this string";
    Result<String> output = handler.failWithValidationErrors(otherwise);

    assertTrue(output.succeeded());
    assertSame(otherwise, output.value());
  }

  @Test
  void failWithValidationErrorsReturnsPassedValueWhenHasNoErrors() {
    assertTrue(handler.getErrors().isEmpty());

    String otherwise = "should return this string";
    Result<String> output = handler.failWithValidationErrors(otherwise);

    assertTrue(output.succeeded());
    assertSame(otherwise, output.value());
  }

  @Test
  void hasAnyReturnsTrueWhenContainsErrorOfPassedType() {
    handler.handleAnyResult(SERVER_ERROR_RESULT, INVALID_ITEM_ID, SUCCEEDED_RESULT);
    assertEquals(1, handler.getErrors().size());
    assertTrue(handler.getErrors().containsValue(INVALID_ITEM_ID));

    assertTrue(handler.hasAny(INVALID_ITEM_ID, USER_IS_INACTIVE));
  }

  @Test
  void hasAnyReturnsFalseWhenContainsNoErrorsOfPassedType() {
    handler.handleAnyResult(SERVER_ERROR_RESULT, INVALID_ITEM_ID, SUCCEEDED_RESULT);
    assertEquals(1, handler.getErrors().size());
    assertTrue(handler.getErrors().containsValue(INVALID_ITEM_ID));

    assertFalse(handler.hasAny(USER_IS_INACTIVE, ITEM_DOES_NOT_EXIST));
  }

  @Test
  void hasAnyReturnsFalseWhenContainsNoErrors() {
    assertTrue(handler.getErrors().isEmpty());
    assertFalse(handler.hasAny(INVALID_ITEM_ID, USER_IS_INACTIVE));
  }

  @Test
  void hasNoneReturnsFalseWhenContainsErrorOfPassedType() {
    handler.handleAnyResult(SERVER_ERROR_RESULT, INVALID_ITEM_ID, SUCCEEDED_RESULT);
    assertEquals(1, handler.getErrors().size());
    assertTrue(handler.getErrors().containsValue(INVALID_ITEM_ID));

    assertFalse(handler.hasNone(INVALID_ITEM_ID, USER_IS_INACTIVE));
  }

  @Test
  void hasNoneReturnsTrueWhenContainsNoErrorsOfPassedType() {
    handler.handleAnyResult(SERVER_ERROR_RESULT, INVALID_ITEM_ID, SUCCEEDED_RESULT);
    assertEquals(1, handler.getErrors().size());
    assertTrue(handler.getErrors().containsValue(INVALID_ITEM_ID));

    assertTrue(handler.hasNone(USER_IS_INACTIVE, ITEM_DOES_NOT_EXIST));
  }

  @Test
  void hasNoneReturnsTrueWhenContainsNoErrors() {
    assertTrue(handler.getErrors().isEmpty());
    assertTrue(handler.hasNone(INVALID_ITEM_ID));
  }
}
