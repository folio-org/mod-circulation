package api.support.fixtures;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.scheduledAgeToLostUrl;
import static api.support.http.ResourceClient.forServicePoints;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;

import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;

import api.support.builders.ItemBuilder;
import api.support.fixtures.policies.PoliciesActivationFixture;
import api.support.http.TimedTaskClient;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.val;

public final class AgeToLostFixture {
  private final PoliciesActivationFixture policiesActivation;
  private final LostItemFeePoliciesFixture lostItemFeePoliciesFixture;
  private final ItemsFixture itemsFixture;
  private final CheckOutFixture checkOutFixture;
  private final UsersFixture usersFixture;
  private final LoansFixture loansFixture;
  private final TimedTaskClient timedTaskClient;

  public AgeToLostFixture() {
    policiesActivation = new PoliciesActivationFixture();
    lostItemFeePoliciesFixture = new LostItemFeePoliciesFixture();
    itemsFixture = new ItemsFixture();
    usersFixture = new UsersFixture();
    checkOutFixture = new CheckOutFixture(usersFixture, new ServicePointsFixture(forServicePoints()));
    loansFixture = new LoansFixture();
    timedTaskClient = new TimedTaskClient(getOkapiHeadersFromContext());
  }

  public AgeToLostResult createAgedToLostLoan() {
    policiesActivation.useLostItemPolicy(
      lostItemFeePoliciesFixture.ageToLostAfterOneMinute().getId());

    val user = usersFixture.james();
    val item = itemsFixture.basedUponNod(ItemBuilder::withRandomBarcode);
    val loan = checkOutFixture
      .checkOutByBarcode(item, user, getLoanOverdueDate().minusMinutes(2));

    timedTaskClient.start(scheduledAgeToLostUrl(), 204, "scheduled-age-to-lost");

    return new AgeToLostResult(
      loansFixture.getLoanById(loan.getId()),
      itemsFixture.getById(item.getId()),
      user);
  }

  private DateTime getLoanOverdueDate() {
    return now(UTC).minusWeeks(3);
  }

  @Getter
  @RequiredArgsConstructor
  public static final class AgeToLostResult {
    private final IndividualResource loan;
    private final IndividualResource item;
    private final IndividualResource user;
  }
}
