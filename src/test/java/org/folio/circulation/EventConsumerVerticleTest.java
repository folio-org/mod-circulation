package org.folio.circulation;

import static api.support.APITestContext.TENANT_ID;
import static api.support.Wait.waitFor;
import static api.support.Wait.waitForValue;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.circulation.EventConsumerVerticle.buildConfig;
import static org.folio.circulation.domain.events.DomainEventType.CIRCULATION_RULES_UPDATED;
import static org.folio.circulation.rules.cache.CirculationRulesCache.getInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

import java.util.Map;
import java.util.UUID;

import org.folio.circulation.domain.events.DomainEventPayloadType;
import org.folio.circulation.rules.cache.Rules;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import api.support.APITestContext;
import api.support.APITests;
import api.support.builders.LoanPolicyBuilder;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class EventConsumerVerticleTest extends APITests {
  private final String CIRCULATION_RULES_TOPIC = kafkaHelper.buildTopicName("circulation", "rules");

  @BeforeEach
  public void beforeEach() {
    getInstance().dropCache();
  }

  @Test
  void circulationRulesUpdateEventConsumerJoinsVacantConsumerSubgroup() {
    final String subgroup0 = kafkaHelper.buildConsumerSubgroupId(CIRCULATION_RULES_UPDATED, 0);
    final String subgroup1 = kafkaHelper.buildConsumerSubgroupId(CIRCULATION_RULES_UPDATED, 1);
    final String subgroup2 = kafkaHelper.buildConsumerSubgroupId(CIRCULATION_RULES_UPDATED, 2);

    // verticle1 has already been deployed, its consumer is a member of its own subgroup0
    kafkaHelper.verifyConsumerGroups(Map.of(subgroup0, 1));

    // verticle2 is deployed, new consumer is created in a separate subgroup1
    String verticle2DeploymentId = deployVerticle();
    kafkaHelper.verifyConsumerGroups(Map.of(subgroup0, 1, subgroup1, 1));

    // verticle3 is deployed, new consumer is created in a separate subgroup2
    deployVerticle();
    kafkaHelper.verifyConsumerGroups(Map.of(subgroup0, 1, subgroup1, 1, subgroup2, 1));

    // verticle2 is undeployed, its consumer and subgroup1 are removed
    undeployVerticle(verticle2DeploymentId);
    waitFor(kafkaHelper.deleteConsumerGroup(subgroup1));
    kafkaHelper.verifyConsumerGroups(Map.of(subgroup0, 1, subgroup2, 1));

    // verticle4 is deployed, the now vacant subgroup1 is recreated and new consumer joins it
    String verticle4DeploymentId = deployVerticle();
    kafkaHelper.verifyConsumerGroups(Map.of(subgroup0, 1, subgroup1, 1, subgroup2, 1));

    // verticle4 is undeployed, its consumer is removed, but empty subgroup1 is not removed
    undeployVerticle(verticle4DeploymentId);
    kafkaHelper.verifyConsumerGroups(Map.of(subgroup0, 1, subgroup1, 0, subgroup2, 1));

    // verticle5 is deployed, new consumer joins the empty subgroup1
    deployVerticle();
    kafkaHelper.verifyConsumerGroups(Map.of(subgroup0, 1, subgroup1, 1, subgroup2, 1));
  }

  @Test
  void circulationRulesUpdateEventsAreDeliveredToMultipleConsumers() {
    final String subgroup0 = kafkaHelper.buildConsumerSubgroupId(CIRCULATION_RULES_UPDATED, 0);
    final String subgroup1 = kafkaHelper.buildConsumerSubgroupId(CIRCULATION_RULES_UPDATED, 1);

    // first verticle has been deployed beforehand, so we should already see subgroup0 with 1 consumer
    kafkaHelper.verifyConsumerGroups(Map.of(subgroup0, 1));
    deployVerticle();
    kafkaHelper.verifyConsumerGroups(Map.of(subgroup0, 1, subgroup1, 1));

    int initialOffsetForSubgroup0 = kafkaHelper.getOffsetForCirculationRulesUpdateEvents(0);
    int initialOffsetForSubgroup1 = kafkaHelper.getOffsetForCirculationRulesUpdateEvents(1);

    JsonObject rules = circulationRulesFixture.getRules().getJson();
    kafkaHelper.publishUpdateEvent(CIRCULATION_RULES_TOPIC, rules, rules);

    waitForValue(() -> kafkaHelper.getOffsetForCirculationRulesUpdateEvents(0), initialOffsetForSubgroup0 + 1);
    waitForValue(() -> kafkaHelper.getOffsetForCirculationRulesUpdateEvents(1), initialOffsetForSubgroup1 + 1);
  }

  @Test
  void circulationRulesUpdateEventUpdatesCirculationRulesCache() {
    UUID nonLoanableLoanPolicyId = UUID.randomUUID();
    use(new LoanPolicyBuilder().withLoanable(false).withId(nonLoanableLoanPolicyId));
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();
    UserResource user = usersFixture.steve();
    Response response = checkOutFixture.attemptCheckOutByBarcode(item, user);
    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    UUID loanableLoanPolicyId = loanPoliciesFixture.canCirculateRolling().getId();
    JsonObject originalRules = circulationRulesFixture.getRules().getJson();
    String newRulesAsText = originalRules.getString("rulesAsText")
      .replace(nonLoanableLoanPolicyId.toString(), loanableLoanPolicyId.toString());
    JsonObject newRules = originalRules.copy().put("rulesAsText", newRulesAsText);

    int initialOffset = kafkaHelper.getOffsetForCirculationRulesUpdateEvents();
    kafkaHelper.publishUpdateEvent(CIRCULATION_RULES_TOPIC, originalRules, newRules);
    waitForValue(kafkaHelper::getOffsetForCirculationRulesUpdateEvents, initialOffset + 1);
    assertThat(getInstance().getRules(TENANT_ID).getRulesAsText(), equalTo(newRulesAsText));

    checkOutFixture.checkOutByBarcode(item, user); // checks for status 201
  }

  @Test
  void invalidCirculationRulesEventsDoNotAffectCachedRules() {
    warmUpCirculationRulesCache();
    JsonObject originalRulesJson = circulationRulesFixture.getRules().getJson();

    Rules originalCachedRules = getInstance().getRules(TENANT_ID);
    assertThat(originalCachedRules.getRulesAsText(), not(emptyOrNullString()));

    JsonObject newRulesJson = originalRulesJson.copy().put("rulesAsText", buildNewRules());
    assertThat(originalRulesJson, not(equalTo(newRulesJson)));
    JsonObject eventTemplate = kafkaHelper.buildUpdateEvent(originalRulesJson, newRulesJson);

    JsonObject eventWithoutTenant = eventTemplate.copy();
    eventWithoutTenant.remove("tenant");

    JsonObject eventWithoutType = eventTemplate.copy();
    eventWithoutType.remove("type");

    JsonObject eventWithoutTimestamp = eventTemplate.copy();
    eventWithoutTimestamp.remove("timestamp");

    JsonObject eventWithoutData = eventTemplate.copy();
    eventWithoutData.remove("data");

    JsonObject eventWithoutOldRules = eventTemplate.copy();
    eventWithoutOldRules.getJsonObject("data").remove("old");

    JsonObject eventWithoutNewRules = eventTemplate.copy();
    eventWithoutNewRules.getJsonObject("data").remove("new");

    JsonObject eventWithoutNewRulesAsText = eventTemplate.copy();
    eventWithoutNewRulesAsText.getJsonObject("data").getJsonObject("new").remove("rulesAsText");

    JsonObject eventWithEmptyNewRulesAsText = eventTemplate.copy();
    eventWithEmptyNewRulesAsText.getJsonObject("data").getJsonObject("new").put("rulesAsText", "");

    int initialOffset = kafkaHelper.getOffsetForCirculationRulesUpdateEvents();
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutTenant);
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutType);
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutTimestamp);
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutData);
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutOldRules);
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutNewRules);
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutNewRulesAsText);
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, eventWithEmptyNewRulesAsText);
    waitForValue(kafkaHelper::getOffsetForCirculationRulesUpdateEvents, initialOffset + 8);

    Rules newCachedRules = getInstance().getRules(TENANT_ID);
    assertThat(originalCachedRules.getReloadTimestamp(), equalTo(newCachedRules.getReloadTimestamp()));
    assertThat(originalCachedRules.getRulesAsText(), equalTo(newCachedRules.getRulesAsText()));
  }

  @Test
  void circulationRulesUpdateEventDoesNotAffectEmptyCache() {
    JsonObject originalRulesJson = circulationRulesFixture.getRules().getJson();
    JsonObject newRulesJson = originalRulesJson.copy().put("rulesAsText", buildNewRules());
    assertThat(newRulesJson, not(equalTo(originalRulesJson)));

    getInstance().dropCache();
    assertThat(getInstance().getRules(TENANT_ID), nullValue()); // cache is empty

    int initialOffset = kafkaHelper.getOffsetForCirculationRulesUpdateEvents();
    kafkaHelper.publishUpdateEvent(CIRCULATION_RULES_TOPIC, originalRulesJson, newRulesJson);
    waitForValue(kafkaHelper::getOffsetForCirculationRulesUpdateEvents, initialOffset + 1);

    assertThat(getInstance().getRules(TENANT_ID), nullValue()); // cache is still empty
  }

  @Test
  void outdatedCirculationRulesUpdateEventDoesNotAffectCache() {
    warmUpCirculationRulesCache();
    JsonObject originalRulesJson = circulationRulesFixture.getRules().getJson();
    Rules originalCachedRules = getInstance().getRules(TENANT_ID);
    String originalCachedRulesText = originalCachedRules.getRulesAsText();
    assertThat(originalCachedRulesText, not(emptyOrNullString()));
    assertThat(originalCachedRulesText, equalTo(originalRulesJson.getString("rulesAsText")));

    JsonObject newRulesJson = originalRulesJson.copy().put("rulesAsText", buildNewRules());
    assertThat(originalRulesJson, not(equalTo(newRulesJson)));
    JsonObject event = kafkaHelper.buildUpdateEvent(originalRulesJson, newRulesJson)
      .put("timestamp", ClockUtil.getInstant().minus(1, MINUTES).toEpochMilli());

    int initialOffset = kafkaHelper.getOffsetForCirculationRulesUpdateEvents();
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, event);
    waitForValue(kafkaHelper::getOffsetForCirculationRulesUpdateEvents, initialOffset + 1);

    Rules newCachedRules = getInstance().getRules(TENANT_ID);
    assertThat(newCachedRules.getRulesAsText(), equalTo(originalCachedRules.getRulesAsText()));
    assertThat(newCachedRules.getReloadTimestamp(), equalTo(originalCachedRules.getReloadTimestamp()));
  }

  @ParameterizedTest
  @EnumSource(value = DomainEventPayloadType.class, names = "UPDATED", mode = EXCLUDE)
  void circulationRulesEventOfUnsupportedTypeIsIgnored(DomainEventPayloadType eventType) {
    warmUpCirculationRulesCache();
    JsonObject originalRulesJson = circulationRulesFixture.getRules().getJson();
    JsonObject newRulesJson = originalRulesJson.copy().put("rulesAsText", buildNewRules());
    assertThat(originalRulesJson, not(equalTo(newRulesJson)));
    JsonObject event = kafkaHelper.buildUpdateEvent(originalRulesJson, newRulesJson)
      .put("type", eventType.name());
    Rules originalCachedRules = getInstance().getRules(TENANT_ID);

    int initialOffset = kafkaHelper.getOffsetForCirculationRulesUpdateEvents();
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, event);
    waitForValue(kafkaHelper::getOffsetForCirculationRulesUpdateEvents, initialOffset + 1);

    Rules newCachedRules = getInstance().getRules(TENANT_ID);
    assertThat(newCachedRules.getRulesAsText(), equalTo(originalCachedRules.getRulesAsText()));
    assertThat(newCachedRules.getReloadTimestamp(), equalTo(originalCachedRules.getReloadTimestamp()));
  }

  private void warmUpCirculationRulesCache() {
    tenantActivationFixture.postTenant();
  }

  private String buildNewRules() {
    return circulationRulesFixture.soleFallbackPolicyRule(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.pageRequestPolicy().getId(),
      noticePoliciesFixture.inactiveNotice().getId(),
      overdueFinePoliciesFixture.noOverdueFine().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());
  }

//  private static int getOffsetForCirculationRulesUpdateEvents() {
//    return getOffsetForCirculationRulesUpdateEvents(0);
//  }
//
//  private static int getOffsetForCirculationRulesUpdateEvents(int subgroupOrdinal) {
//    return getOffset(CIRCULATION_RULES_TOPIC,
//      buildConsumerSubgroupId(CIRCULATION_RULES_UPDATED, subgroupOrdinal));
//  }
//
//  private static String buildConsumerSubgroupId(DomainEventType eventType, int subgroupOrdinal) {
//    return String.format("%s-subgroup-%d", buildConsumerGroupId(eventType), subgroupOrdinal);
//  }
//
//  private static String buildConsumerGroupId(DomainEventType eventType) {
//    return format("%s.%s-%s", eventType, PomReader.INSTANCE.getModuleName(), PomReader.INSTANCE.getVersion());
//  }

  private static String deployVerticle() {
    return APITestContext.deployVerticle(EventConsumerVerticle.class, buildConfig());
  }

  private static void undeployVerticle(String deploymentId) {
    APITestContext.undeployVerticle(deploymentId);
  }

}
