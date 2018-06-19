package api.loans;

import api.support.APITests;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.ValidationError;
import org.hamcrest.Matcher;

abstract class RenewalTests extends APITests {
  abstract Response attemptRenewal(IndividualResource user, IndividualResource item);
  abstract IndividualResource renew(IndividualResource user, IndividualResource item);
  abstract Matcher<ValidationError> matchUserRelatedParameter(IndividualResource user);
  abstract Matcher<ValidationError> matchItemRelatedParameter(IndividualResource item);
}
