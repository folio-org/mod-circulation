package org.folio.circulation;

import static api.support.APITestContext.TENANT_ID;
import static api.support.Wait.waitForValue;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.circulation.EventConsumerVerticle.buildConfig;
import static org.folio.circulation.domain.events.DomainEventType.CIRCULATION_RULES_UPDATED;
import static org.folio.circulation.rules.cache.CirculationRulesCache.getInstance;
import static org.folio.kafka.services.KafkaEnvironmentProperties.environment;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

import java.util.Collection;
import java.util.List;
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
  private static final String CIRCULATION_RULES_TOPIC = buildTopicName("circulation", "rules");
  private static final String CIRCULATION_RULES_UPDATED_EVENT_CONSUMER_GROUP_ID_PATTERN =
    CIRCULATION_RULES_UPDATED.name() + "\\.mod-circulation-[\\d.]+_\\d+_\\d+";

  @BeforeEach
  public void beforeEach() {
    getInstance().dropCache();
  }

  @Test
  void circulationRulesUpdateEventsAreDeliveredToMultipleConsumers() {
    // first verticle has been deployed beforehand, so we should already see a group with 1 consumer
    Collection<String> groupsBeforeDeployment = kafkaHelper.getConsumerGroups(
      CIRCULATION_RULES_UPDATED_EVENT_CONSUMER_GROUP_ID_PATTERN, 1);
    assertThat(groupsBeforeDeployment, hasSize(1));
    String group1 = groupsBeforeDeployment.iterator().next();
    kafkaHelper.verifyConsumerGroups(Map.of(group1, 1));

    String secondVerticleId = deployVerticle();
    // after deploying second verticle we should see 2 groups with 1 consumer each
    Collection<String> groupsAfterDeployment = kafkaHelper.getConsumerGroups(
      CIRCULATION_RULES_UPDATED_EVENT_CONSUMER_GROUP_ID_PATTERN, 2);
    assertThat(groupsAfterDeployment, hasSize(2));
    String group2 = groupsAfterDeployment.stream()
      .filter(groupId -> !groupId.equals(group1))
      .findFirst()
      .orElseThrow();
    kafkaHelper.verifyConsumerGroups(Map.of(group1, 1, group2, 1));

    int initialOffsetForGroup1 = kafkaHelper.getOffset(CIRCULATION_RULES_TOPIC, group1);
    int initialOffsetForGroup2 = kafkaHelper.getOffset(CIRCULATION_RULES_TOPIC, group2);

    JsonObject rules = circulationRulesFixture.getRules().getJson();
    publishCirculationRulesUpdateEvent(rules, rules);

    // verify that both consumer groups have received and processed the event
    waitForValue(() -> kafkaHelper.getOffset(CIRCULATION_RULES_TOPIC, group1), initialOffsetForGroup1 + 1);
    waitForValue(() -> kafkaHelper.getOffset(CIRCULATION_RULES_TOPIC, group2), initialOffsetForGroup2 + 1);

    // undeploy second verticle and delete its consumer group
    undeployVerticle(secondVerticleId);
    kafkaHelper.deleteConsumerGroup(group2);
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

    int initialOffset = getOffsetForCirculationRulesUpdateEvents();
    publishCirculationRulesUpdateEvent(originalRules, newRules);
    waitForValue(EventConsumerVerticleTest::getOffsetForCirculationRulesUpdateEvents, initialOffset + 1);
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
    JsonObject eventTemplate = buildUpdateEvent(originalRulesJson, newRulesJson);

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

    int initialOffset = getOffsetForCirculationRulesUpdateEvents();
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutTenant);
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutType);
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutTimestamp);
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutData);
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutOldRules);
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutNewRules);
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutNewRulesAsText);
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, eventWithEmptyNewRulesAsText);
    waitForValue(EventConsumerVerticleTest::getOffsetForCirculationRulesUpdateEvents, initialOffset + 8);

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

    int initialOffset = getOffsetForCirculationRulesUpdateEvents();
    publishCirculationRulesUpdateEvent(originalRulesJson, newRulesJson);
    waitForValue(EventConsumerVerticleTest::getOffsetForCirculationRulesUpdateEvents, initialOffset + 1);

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
    JsonObject event = buildUpdateEvent(originalRulesJson, newRulesJson)
      .put("timestamp", ClockUtil.getInstant().minus(1, MINUTES).toEpochMilli());

    int initialOffset = getOffsetForCirculationRulesUpdateEvents();
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, event);
    waitForValue(EventConsumerVerticleTest::getOffsetForCirculationRulesUpdateEvents, initialOffset + 1);

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
    JsonObject event = buildUpdateEvent(originalRulesJson, newRulesJson)
      .put("type", eventType.name());
    Rules originalCachedRules = getInstance().getRules(TENANT_ID);

    int initialOffset = getOffsetForCirculationRulesUpdateEvents();
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, event);
    waitForValue(EventConsumerVerticleTest::getOffsetForCirculationRulesUpdateEvents, initialOffset + 1);

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

  private static int getOffsetForCirculationRulesUpdateEvents() {
    return kafkaHelper.getOffset(CIRCULATION_RULES_TOPIC, getCirculationRulesUpdateEventConsumerGroupId());
  }

  private void publishCirculationRulesUpdateEvent(JsonObject oldRules, JsonObject newRules) {
    kafkaHelper.publishEvent(CIRCULATION_RULES_TOPIC, buildUpdateEvent(oldRules, newRules));
  }

  private static JsonObject buildUpdateEvent(JsonObject oldVersion, JsonObject newVersion) {
    return new JsonObject()
      .put("id", randomId())
      .put("tenant", TENANT_ID)
      .put("type", "UPDATED")
      .put("timestamp", System.currentTimeMillis())
      .put("data", new JsonObject()
        .put("old", oldVersion)
        .put("new", newVersion));
  }

  private static String buildTopicName(String module, String topic) {
    return format("%s.%s.%s.%s", environment(), TENANT_ID, module, topic);
  }

  private static String deployVerticle() {
    return APITestContext.deployVerticle(EventConsumerVerticle.class, buildConfig());
  }

  private static void undeployVerticle(String deploymentId) {
    APITestContext.undeployVerticle(deploymentId);
  }

  private static String getCirculationRulesUpdateEventConsumerGroupId() {
    List<String> groupIds = kafkaHelper.findConsumerGroupIds(CIRCULATION_RULES_UPDATED_EVENT_CONSUMER_GROUP_ID_PATTERN);
    assertThat("Expected exactly one consumer group", groupIds, hasSize(1));
    return groupIds.getFirst();
  }



}
