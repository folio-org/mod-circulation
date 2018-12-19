package api.loans;

import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.ValidationError;
import org.hamcrest.Matcher;

public class RenewByIdTests extends RenewalAPITests {
  @Override
  Response attemptRenewal(IndividualResource user, IndividualResource item) {
    return loansFixture.attemptRenewalById(user, item);
  }

  @Override
  IndividualResource renew(IndividualResource user, IndividualResource item) {
    return loansFixture.renewLoanById(user, item);
  }

  @Override
  Matcher<ValidationError> hasUserRelatedParameter(IndividualResource user) {
    return hasUUIDParameter("userId", user.getId());
  }

  @Override
  Matcher<ValidationError> hasItemRelatedParameter(IndividualResource item) {
    return hasUUIDParameter("itemId", item.getId());
  }

  @Override
  Matcher<ValidationError> hasItemNotFoundMessage(IndividualResource item) {
    return hasMessage(String.format("No item with ID %s exists", item.getId()));
  }
}
