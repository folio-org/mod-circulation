package api.support.fixtures.policies;

import static api.support.RestAssuredClient.defaultRestAssuredClient;
import static api.support.http.InterfaceUrls.circulationRulesUrl;
import static api.support.http.ResourceClient.forFixedDueDateSchedules;
import static api.support.http.ResourceClient.forLoanPolicies;
import static api.support.http.ResourceClient.forNoticePolicies;
import static api.support.http.ResourceClient.forRequestPolicies;
import static api.support.http.api.support.NamedQueryStringParameter.namedParameter;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import api.support.RestAssuredClient;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fixtures.CirculationRulesFixture;
import api.support.fixtures.LoanPoliciesFixture;
import api.support.fixtures.LocationsFixture;
import api.support.fixtures.LostItemFeePoliciesFixture;
import api.support.fixtures.NoticePoliciesFixture;
import api.support.fixtures.OverdueFinePoliciesFixture;
import api.support.fixtures.RequestPoliciesFixture;
import api.support.http.QueryStringParameter;

// It is delegated by lombok in APITests class
@SuppressWarnings("unused")
public final class PoliciesActivationFixture {
  private static final Logger log = LoggerFactory.getLogger(PoliciesActivationFixture.class);

  private final RestAssuredClient restAssuredClient;
  private final LoanPoliciesFixture loanPoliciesFixture;
  private final RequestPoliciesFixture requestPoliciesFixture;
  private final NoticePoliciesFixture noticePoliciesFixture;
  private final OverdueFinePoliciesFixture overdueFinePoliciesFixture;
  private final LostItemFeePoliciesFixture lostItemFeePoliciesFixture;
  private final CirculationRulesFixture circulationRulesFixture;
  private final LocationsFixture locationsFixture;

  public PoliciesActivationFixture() {
    restAssuredClient = defaultRestAssuredClient();

    loanPoliciesFixture = new LoanPoliciesFixture(forLoanPolicies(), forFixedDueDateSchedules());
    requestPoliciesFixture = new RequestPoliciesFixture(forRequestPolicies());
    noticePoliciesFixture = new NoticePoliciesFixture(forNoticePolicies());
    overdueFinePoliciesFixture = new OverdueFinePoliciesFixture();
    lostItemFeePoliciesFixture = new LostItemFeePoliciesFixture();
    circulationRulesFixture = new CirculationRulesFixture(restAssuredClient);
    locationsFixture = new LocationsFixture();
  }

  public PoliciesActivation.PoliciesActivationBuilder rollingDefaultPolicies() {
    return PoliciesActivation.builder()
      .loanPolicy(loanPoliciesFixture.canCirculateRolling())
      .requestPolicy(requestPoliciesFixture.allowAllRequestPolicy())
      .noticePolicy(noticePoliciesFixture.activeNotice())
      .overduePolicy(overdueFinePoliciesFixture.facultyStandard())
      .lostItemPolicy(lostItemFeePoliciesFixture.facultyStandard());
  }

  //Needs to be done each time as some tests manipulate the rules
  public void useDefaultRollingPolicyCirculationRules() {
    log.info("Using rolling loan policy as fallback policy");

    use(rollingDefaultPolicies());
  }

  public void useExampleFixedPolicyCirculationRules() {
    log.info("Using fixed loan policy as fallback policy");

    use(rollingDefaultPolicies().loanPolicy(loanPoliciesFixture.canCirculateFixed()));
  }

  public void useFallbackPolicies(UUID loanPolicyId, UUID requestPolicyId,
    UUID noticePolicyId, UUID overdueFinePolicyId, UUID lostItemFeePolicyId) {

    circulationRulesFixture.updateCirculationRules(loanPolicyId, requestPolicyId,
      noticePolicyId, overdueFinePolicyId, lostItemFeePolicyId);

    warmUpApplyEndpoint();
  }


  /**
   * This method uses the loan policy, allowAllRequestPolicy request policy,
   * inactiveNotice notice policy, facultyStandard overdue fine policy from
   * the loanPolicyBuilder.
   * @param loanPolicyBuilder - loan policy builder.
   */
  public void setFallbackPolicies(LoanPolicyBuilder loanPolicyBuilder) {
    use(rollingDefaultPolicies()
      .loanPolicy(loanPoliciesFixture.create(loanPolicyBuilder))
      .noticePolicy(noticePoliciesFixture.inactiveNotice()));
  }

  /**
   * This method uses the loan policy, allowAllRequestPolicy request policy,
   * activeNotice notice policy, facultyStandard overdue fine policy from
   * the loanPolicyBuilder.
   * @param loanPolicyBuilder - loan policy builder.
   */
  public void use(LoanPolicyBuilder loanPolicyBuilder) {
    use(rollingDefaultPolicies()
      .loanPolicy(loanPoliciesFixture.create(loanPolicyBuilder)));
  }

  public void use(PoliciesActivation.PoliciesActivationBuilder builder) {
    final PoliciesActivation policies = builder.build();

    useFallbackPolicies(policies.getLoanPolicy().getId(),
      policies.getRequestPolicy().getId(),
      policies.getNoticePolicy().getId(),
      policies.getOverduePolicy().getId(),
      policies.getLostItemPolicy().getId());
  }

  /**
   * This method uses the loan policy, allowAllRequestPolicy request policy,
   * activeNotice notice policy, facultyStandard overdue fine policy from
   * the loanPolicyBuilder and noticePolicyBuilder.
   * @param loanPolicyBuilder - loan policy builder.
   */
  public void use(LoanPolicyBuilder loanPolicyBuilder,
    NoticePolicyBuilder noticePolicyBuilder) {

    use(rollingDefaultPolicies()
      .loanPolicy(loanPoliciesFixture.create(loanPolicyBuilder))
      .noticePolicy(noticePoliciesFixture.create(noticePolicyBuilder)));
  }

  /**
   * This method uses notice policy, canCirculateRolling loan policy,
   * allowAllRequestPolicy request policy,
   * facultyStandard overdue fine policy from
   * the loanPolicyBuilder.
   *
   * @param noticePolicy - notice policy.
   */
  public void use(NoticePolicyBuilder noticePolicy) {
    use(rollingDefaultPolicies()
      .noticePolicy(noticePoliciesFixture.create(noticePolicy)));
  }

  public void useLostItemPolicy(UUID lostItemFeePolicyId) {
    final PoliciesActivation policies = rollingDefaultPolicies().build();

    useFallbackPolicies(policies.getLoanPolicy().getId(),
      policies.getRequestPolicy().getId(),
      policies.getNoticePolicy().getId(),
      policies.getOverduePolicy().getId(),
      lostItemFeePolicyId);
  }

  /**
   * This method uses the loan policy, allowAllRequestPolicy request policy,
   * activeNotice notice policy, facultyStandard overdue fine policy from
   * the loanPolicyBuilder.
   * @param loanPolicyBuilder - loan policy builder.
   */
  public void useWithActiveNotice(LoanPolicyBuilder loanPolicyBuilder) {
    use(rollingDefaultPolicies()
      .loanPolicy(loanPoliciesFixture.create(loanPolicyBuilder)));
  }

  /**
   * This method uses notice policy, canCirculateRolling loan policy,
   * allowAllRequestPolicy request policy, facultyStandard overdue fine policy from
   * the loanPolicyBuilder.
   * @param noticePolicy - notice policy.
   */
  public void useWithPaging(NoticePolicyBuilder noticePolicy) {
    use(rollingDefaultPolicies()
      .requestPolicy(requestPoliciesFixture.pageRequestPolicy())
      .noticePolicy(noticePoliciesFixture.create(noticePolicy)));
  }

  public void warmUpApplyEndpoint() {
    final URL loanPolicyRulesEndpoint = circulationRulesUrl("/loan-policy");

    final List<QueryStringParameter> parameters = new ArrayList<>();

    parameters.add(namedParameter("item_type_id", UUID.randomUUID().toString()));
    parameters.add(namedParameter("loan_type_id", UUID.randomUUID().toString()));
    parameters.add(namedParameter("patron_type_id", UUID.randomUUID().toString()));
    parameters.add(namedParameter("location_id",
      locationsFixture.mezzanineDisplayCase().getId().toString()));

    restAssuredClient.get(loanPolicyRulesEndpoint, parameters, 200,
      "warm-up-circulation-rules");
  }

  public void setInvalidLoanPolicyReferenceInRules(
    String invalidLoanPolicyReference) {

    circulationRulesFixture.updateCirculationRules(
      circulationRulesFixture.soleFallbackPolicyRule(invalidLoanPolicyReference,
        requestPoliciesFixture.allowAllRequestPolicy().getId().toString(),
        noticePoliciesFixture.inactiveNotice().getId().toString(),
        overdueFinePoliciesFixture.facultyStandard().getId().toString(),
        lostItemFeePoliciesFixture.facultyStandard().getId().toString()));
  }

  public void setInvalidNoticePolicyReferenceInRules(
    String invalidNoticePolicyReference) {

    circulationRulesFixture.updateCirculationRules(
      circulationRulesFixture.soleFallbackPolicyRule(
        loanPoliciesFixture.canCirculateRolling().getId().toString(),
        requestPoliciesFixture.allowAllRequestPolicy().getId().toString(),
        invalidNoticePolicyReference,
        overdueFinePoliciesFixture.facultyStandard().getId().toString(),
        lostItemFeePoliciesFixture.facultyStandard().getId().toString()));
  }
}
