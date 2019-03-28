package org.folio.circulation.domain.validation;

import java.util.Objects;
import java.util.function.Function;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.Result;

public class UniqRequestValidator {

  private final Function<RequestAndRelatedRecords, ? extends HttpFailure> errorSupplier;

  public UniqRequestValidator(Function<RequestAndRelatedRecords, ? extends HttpFailure> errorSupplier) {
    this.errorSupplier = errorSupplier;
  }

  public Result<RequestAndRelatedRecords> refuseWhenRequestIsAlreadyExisted(RequestAndRelatedRecords request) {
    return Result.of(() -> request)
      .failWhen(this::isRequesterHasAlreadyRequestForTheItem, errorSupplier::apply);
  }

  private Result<Boolean> isRequesterHasAlreadyRequestForTheItem(RequestAndRelatedRecords request) {
    return Result.of(() ->
      request
        .getRequestQueue()
        .getRequests()
        .stream()
        .anyMatch(it -> isTheSameRequester(request, it) && it.isOpen())
    );
  }

  private boolean isTheSameRequester(RequestAndRelatedRecords it, Request that) {
    return Objects.equals(it.getUserId(), that.getUserId());
  }
}
