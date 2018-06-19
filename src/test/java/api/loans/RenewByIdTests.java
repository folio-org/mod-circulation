package api.loans;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.ValidationError;
import org.hamcrest.Matcher;

import static api.support.matchers.ValidationErrorMatchers.hasParameter;

public class RenewByIdTests extends RenewalTests {
  @Override
  Response attemptRenewal(IndividualResource user, IndividualResource item) {
    return loansFixture.attemptRenewalById(user, item);
  }

  @Override
  IndividualResource renew(IndividualResource user, IndividualResource item) {
    return loansFixture.renewLoan(user, item);
  }

  @Override
  Matcher<ValidationError> matchUserRelatedParameter(IndividualResource user) {
    return hasParameter("userId", user.getId().toString());
  }

  @Override
  Matcher<ValidationError> matchItemRelatedParameter(IndividualResource item) {
    return hasParameter("itemId", item.getId().toString());
  }
}
