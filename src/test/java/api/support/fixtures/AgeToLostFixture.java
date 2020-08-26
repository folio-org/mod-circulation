package api.support.fixtures;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.scheduledAgeToLostFeeChargingUrl;
import static api.support.http.InterfaceUrls.scheduledAgeToLostUrl;
import static api.support.matchers.ItemMatchers.isAgedToLost;
import static java.time.Clock.fixed;
import static java.time.Instant.ofEpochMilli;
import static org.folio.circulation.support.ClockManager.getClockManager;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTime.now;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;

import api.support.builders.ItemBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import api.support.fixtures.policies.PoliciesActivationFixture;
import api.support.fixtures.policies.PoliciesToActivate;
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

  public AgeToLostFixture(ItemsFixture itemsFixture, UsersFixture usersFixture,
    CheckOutFixture checkOutFixture) {

    this.policiesActivation = new PoliciesActivationFixture();
    this.lostItemFeePoliciesFixture = new LostItemFeePoliciesFixture();
    this.itemsFixture = itemsFixture;
    this.usersFixture = usersFixture;
    this.checkOutFixture = checkOutFixture;
    this.loansFixture = new LoansFixture();
    this.timedTaskClient = new TimedTaskClient(getOkapiHeadersFromContext());
  }

  public AgeToLostResult createAgedToLostLoan() {
    return createAgedToLostLoan(PoliciesToActivate.builder()
    .lostItemPolicy(lostItemFeePoliciesFixture.ageToLostAfterOneMinute()));
  }

  public AgeToLostResult createAgedToLostLoan(PoliciesToActivate.PoliciesToActivateBuilder policiesToUse) {
    policiesActivation.use(policiesToUse);

    val user = usersFixture.james();
    val item = itemsFixture.basedUponNod(ItemBuilder::withRandomBarcode);
    val loan = checkOutFixture.checkOutByBarcode(item, user);

    ageToLost();

    final AgeToLostResult ageToLostResult = new AgeToLostResult(loansFixture.getLoanById(loan.getId()),
      itemsFixture.getById(item.getId()), user);

    assertThat(ageToLostResult.getItem().getJson(), isAgedToLost());

    getClockManager().setDefaultClock();

    return ageToLostResult;
  }

  public AgeToLostResult createLoanAgeToLostAndChargeFees(LostItemFeePolicyBuilder builder) {
    return createLoanAgeToLostAndChargeFees(PoliciesToActivate.builder()
      .lostItemPolicy(lostItemFeePoliciesFixture.create(builder)));
  }

  private AgeToLostResult createLoanAgeToLostAndChargeFees(
    PoliciesToActivate.PoliciesToActivateBuilder policiesToUse) {

    final AgeToLostResult result = createAgedToLostLoan(policiesToUse);

    chargeFees();

    return new AgeToLostResult(loansFixture.getLoanById(result.getLoanId()),
      itemsFixture.getById(result.getItemId()), result.getUser());
  }

  public void ageToLost() {
    moveTimeForwardSixMonths();

    timedTaskClient.start(scheduledAgeToLostUrl(), 204, "scheduled-age-to-lost");

    getClockManager().setDefaultClock();
  }

  public void chargeFees() {
    moveTimeForwardSixMonths();

    timedTaskClient.start(scheduledAgeToLostFeeChargingUrl(), 204,
      "scheduled-age-to-lost-fee-charging");

    getClockManager().setDefaultClock();
  }

  public void ageToLostAndChargeFees() {
    ageToLost();
    chargeFees();
  }

  public Response ageToLostAndAttemptChargeFees() {
    ageToLost();

    moveTimeForwardSixMonths();

    final Response response = timedTaskClient.attemptRun(scheduledAgeToLostFeeChargingUrl(),
      "scheduled-age-to-lost-fee-charging");

    getClockManager().setDefaultClock();

    return response;
  }

  private void moveTimeForwardSixMonths() {
    final Clock fixedClocks = fixed(ofEpochMilli(now().plusMonths(6).getMillis()),
      ZoneOffset.UTC);

    getClockManager().setClock(fixedClocks);
  }

  @Getter
  @RequiredArgsConstructor
  public static final class AgeToLostResult {
    private final IndividualResource loan;
    private final IndividualResource item;
    private final IndividualResource user;

    public UUID getItemId() {
      return item.getId();
    }

    public UUID getLoanId() {
      return loan.getId();
    }
  }
}
