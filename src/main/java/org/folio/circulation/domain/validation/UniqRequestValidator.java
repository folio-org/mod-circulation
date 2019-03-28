package org.folio.circulation.domain.validation;

import static org.folio.circulation.domain.RequestStatus.OPEN_AWAITING_PICKUP;
import static org.folio.circulation.domain.RequestStatus.OPEN_IN_TRANSIT;
import static org.folio.circulation.domain.RequestStatus.OPEN_NOT_YET_FILLED;

import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Function;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.Result;

public class UniqRequestValidator {

  private static final EnumSet<RequestStatus> OPEN_STATUS = EnumSet.of(
    OPEN_AWAITING_PICKUP,
    OPEN_NOT_YET_FILLED,
    OPEN_IN_TRANSIT
  );

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
        .anyMatch(it -> isTheSameRequester(request, it) && OPEN_STATUS.contains(it.getStatus()))
    );
  }

  private boolean isTheSameRequester(RequestAndRelatedRecords it, Request that) {
    return Objects.equals(it.getUserId(), that.getUserId());
  }
}
