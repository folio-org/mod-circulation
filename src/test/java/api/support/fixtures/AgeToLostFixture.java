package api.support.fixtures;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.scheduledAgeToLostFeeChargingUrl;
import static api.support.http.InterfaceUrls.scheduledAgeToLostUrl;
import static api.support.http.ResourceClient.forLoansStorage;
import static api.support.matchers.ItemMatchers.isAgedToLost;
import static java.time.Clock.fixed;
import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.ClockUtil.setClock;
import static org.folio.circulation.support.utils.ClockUtil.setDefaultClock;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;
import java.util.function.UnaryOperator;

import org.folio.circulation.support.http.client.Response;

import api.support.builders.HoldingBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fixtures.policies.PoliciesActivationFixture;
import api.support.fixtures.policies.PoliciesToActivate;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.ResourceClient;
import api.support.http.TimedTaskClient;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.val;

public final class AgeToLostFixture {
  private final PoliciesActivationFixture policiesActivation;
  private final LostItemFeePoliciesFixture lostItemFeePoliciesFixture;
  private final NoticePoliciesFixture noticePoliciesFixture;
  private final ItemsFixture itemsFixture;
  private final CheckOutFixture checkOutFixture;
  private final UsersFixture usersFixture;
  private final TimedTaskClient timedTaskClient;
  private final ResourceClient loanStorageClient;

  public AgeToLostFixture(ItemsFixture itemsFixture, UsersFixture usersFixture,
    CheckOutFixture checkOutFixture, CirculationRulesFixture circulationRulesFixture) {

    this.policiesActivation = new PoliciesActivationFixture(circulationRulesFixture);
    this.lostItemFeePoliciesFixture = new LostItemFeePoliciesFixture();
    this.noticePoliciesFixture = new NoticePoliciesFixture(ResourceClient.forNoticePolicies());
    this.itemsFixture = itemsFixture;
    this.usersFixture = usersFixture;
    this.checkOutFixture = checkOutFixture;
    this.timedTaskClient = new TimedTaskClient(getOkapiHeadersFromContext());
    this.loanStorageClient = forLoansStorage();
  }

  public AgeToLostResult createAgedToLostLoan(LostItemFeePolicyBuilder builder) {
    return createAgedToLostLoan(UnaryOperator.identity(), PoliciesToActivate.builder()
      .lostItemPolicy(lostItemFeePoliciesFixture.create(builder)));
  }

  public AgeToLostResult createAgedToLostLoan() {
    return createAgedToLostLoan(UnaryOperator.identity(), PoliciesToActivate.builder()
    .lostItemPolicy(lostItemFeePoliciesFixture.ageToLostAfterOneMinute()));
  }

  public AgeToLostResult createAgedToLostLoan(NoticePolicyBuilder builder) {
    return createAgedToLostLoan(UnaryOperator.identity(), PoliciesToActivate.builder()
      .lostItemPolicy(lostItemFeePoliciesFixture.ageToLostAfterOneMinute())
      .noticePolicy(noticePoliciesFixture.create(builder)));
  }

  public AgeToLostResult createAgedToLostLoan(UnaryOperator<HoldingBuilder> holdingsBuilder,
    PoliciesToActivate.PoliciesToActivateBuilder policiesToUse) {

    policiesActivation.use(policiesToUse);

    val user = usersFixture.james();
    val item = itemsFixture.basedUponSmallAngryPlanet(holdingsBuilder,
      ItemBuilder::withRandomBarcode);
    val loan = checkOutFixture.checkOutByBarcode(item, user);

    ageToLost();

    final AgeToLostResult ageToLostResult = new AgeToLostResult(loanStorageClient.get(loan),
      itemsFixture.getById(item.getId()), user);

    assertThat(ageToLostResult.getItem().getJson(), isAgedToLost());

    return ageToLostResult;
  }

  public void createAgedToLostLoan(ItemResource item, IndividualResource user) {
    policiesActivation.use(PoliciesToActivate.builder().lostItemPolicy(
      lostItemFeePoliciesFixture.ageToLostAfterOneMinute()));
    checkOutFixture.checkOutByBarcode(item, user);
    ageToLost();
    var itemById = itemsFixture.getById(item.getId());

    assertThat(itemById.getJson(), isAgedToLost());
  }

  public AgeToLostResult createLoanAgeToLostAndChargeFeesWithOverdues(IndividualResource lostPolicy, IndividualResource overduePolicy) {
      return createLoanAgeToLostAndChargeFees(UnaryOperator.identity(),
        PoliciesToActivate.builder()
          .lostItemPolicy(lostPolicy)
          .overduePolicy(overduePolicy)
      );
  }

  public AgeToLostResult createLoanAgeToLostAndChargeFees(LostItemFeePolicyBuilder builder) {
    return createLoanAgeToLostAndChargeFees(UnaryOperator.identity(), builder);
  }

  public AgeToLostResult createLoanAgeToLostAndChargeFees(
    LostItemFeePolicyBuilder lostItemFeePolicyBuilder, NoticePolicyBuilder noticePolicyBuilder) {

    return createLoanAgeToLostAndChargeFeesWithNotice(UnaryOperator.identity(),
      lostItemFeePolicyBuilder, noticePolicyBuilder);
  }

  public AgeToLostResult createLoanAgeToLostAndChargeFeesWithNotice(
    LostItemFeePolicyBuilder lostItemFeePolicyBuilder, NoticePolicyBuilder noticePolicyBuilder) {

    return createLoanAgeToLostAndChargeFeesWithNotice(UnaryOperator.identity(),
      lostItemFeePolicyBuilder, noticePolicyBuilder);
  }

  public AgeToLostResult createLoanAgeToLostAndChargeFees(
    UnaryOperator<HoldingBuilder> holdingsBuilder, LostItemFeePolicyBuilder builder) {

    return createLoanAgeToLostAndChargeFees(holdingsBuilder, PoliciesToActivate.builder()
      .lostItemPolicy(lostItemFeePoliciesFixture.create(builder)));
  }

  public AgeToLostResult createLoanAgeToLostAndChargeFeesWithNotice(
    UnaryOperator<HoldingBuilder> holdingsBuilder, LostItemFeePolicyBuilder lostItemPolicyBuilder,
    NoticePolicyBuilder noticePolicyBuilder) {

    return createLoanAgeToLostAndChargeFees(holdingsBuilder, PoliciesToActivate.builder()
      .noticePolicy(noticePoliciesFixture.create(noticePolicyBuilder))
      .lostItemPolicy(lostItemFeePoliciesFixture.create(lostItemPolicyBuilder)));
  }

  private AgeToLostResult createLoanAgeToLostAndChargeFees(UnaryOperator<HoldingBuilder> builder,
    PoliciesToActivate.PoliciesToActivateBuilder policiesToUse) {

    final AgeToLostResult result = createAgedToLostLoan(builder, policiesToUse);

    chargeFees();

    return new AgeToLostResult(loanStorageClient.get(result.getLoanId()),
      itemsFixture.getById(result.getItemId()), result.getUser());
  }

  public void ageToLost() {
    moveTimeForwardForAgeToLost();

    timedTaskClient.start(scheduledAgeToLostUrl(), 204, "scheduled-age-to-lost");

    setDefaultClock();
  }

  public void chargeFees() {
    moveTimeForwardForChargeFee();

    timedTaskClient.start(scheduledAgeToLostFeeChargingUrl(), 204,
      "scheduled-age-to-lost-fee-charging");

    setDefaultClock();
  }

  public void ageToLostAndChargeFees() {
    ageToLost();
    chargeFees();
  }

  public Response ageToLostAndAttemptChargeFees() {
    ageToLost();

    moveTimeForwardForChargeFee();

    final Response response = timedTaskClient.attemptRun(scheduledAgeToLostFeeChargingUrl(),
      "scheduled-age-to-lost-fee-charging");

    setDefaultClock();

    return response;
  }

  private void moveTimeForwardForAgeToLost() {
    moveTimeForward(6);
  }

  private void moveTimeForwardForChargeFee() {
    moveTimeForward(8);
  }

  private void moveTimeForward(int weeks) {
    setClock(fixed(getZonedDateTime().plusWeeks(weeks).toInstant(), UTC));
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
