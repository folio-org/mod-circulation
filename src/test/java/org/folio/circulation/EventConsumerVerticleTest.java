package org.folio.circulation;

import static api.support.APITestContext.TENANT_ID;
import static api.support.Wait.waitForValue;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toMap;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.circulation.EventConsumerVerticle.buildConfig;
import static org.folio.circulation.domain.events.DomainEventType.CIRCULATION_RULES_UPDATED;
import static org.folio.circulation.rules.cache.CirculationRulesCache.getInstance;
import static org.folio.kafka.services.KafkaEnvironmentProperties.environment;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.folio.circulation.domain.events.DomainEventPayloadType;
import org.folio.circulation.domain.events.DomainEventType;
import org.folio.circulation.rules.cache.Rules;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.ClockUtil;
import org.folio.util.pubsub.support.PomReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import api.support.APITestContext;
import api.support.APITests;
import api.support.builders.LoanPolicyBuilder;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.admin.ConsumerGroupDescription;
import io.vertx.kafka.admin.ConsumerGroupListing;
import io.vertx.kafka.admin.MemberDescription;
import io.vertx.kafka.client.common.TopicPartition;
import io.vertx.kafka.client.consumer.OffsetAndMetadata;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class EventConsumerVerticleTest extends APITests {
  private static final String CIRCULATION_RULES_TOPIC = buildTopicName("circulation", "rules");

  @BeforeEach
  public void beforeEach() {
    getInstance().dropCache();
  }

  @Test
  void circulationRulesUpdateEventConsumerJoinsVacantConsumerSubgroup() {
    final String subgroup0 = buildConsumerSubgroupId(CIRCULATION_RULES_UPDATED, 0);
    final String subgroup1 = buildConsumerSubgroupId(CIRCULATION_RULES_UPDATED, 1);
    final String subgroup2 = buildConsumerSubgroupId(CIRCULATION_RULES_UPDATED, 2);

    // verticle1 has already been deployed, its consumer is a member of its own subgroup0
    verifyConsumerGroups(Map.of(subgroup0, 1));

    // verticle2 is deployed, new consumer is created in a separate subgroup1
    String verticle2DeploymentId = deployVerticle();
    verifyConsumerGroups(Map.of(subgroup0, 1, subgroup1, 1));

    // verticle3 is deployed, new consumer is created in a separate subgroup2
    deployVerticle();
    verifyConsumerGroups(Map.of(subgroup0, 1, subgroup1, 1, subgroup2, 1));

    // verticle2 is undeployed, its consumer and subgroup1 are removed
    undeployVerticle(verticle2DeploymentId);
    waitFor(kafkaAdminClient.deleteConsumerGroups(List.of(subgroup1)));
    verifyConsumerGroups(Map.of(subgroup0, 1, subgroup2, 1));

    // verticle4 is deployed, the now vacant subgroup1 is recreated and new consumer joins it
    String verticle4DeploymentId = deployVerticle();
    verifyConsumerGroups(Map.of(subgroup0, 1, subgroup1, 1, subgroup2, 1));

    // verticle4 is undeployed, its consumer is removed, but empty subgroup1 is not removed
    undeployVerticle(verticle4DeploymentId);
    verifyConsumerGroups(Map.of(subgroup0, 1, subgroup1, 0, subgroup2, 1));

    // verticle5 is deployed, new consumer joins the empty subgroup1
    deployVerticle();
    verifyConsumerGroups(Map.of(subgroup0, 1, subgroup1, 1, subgroup2, 1));
  }

  @Test
  void circulationRulesUpdateEventsAreDeliveredToMultipleConsumers() {
    final String subgroup0 = buildConsumerSubgroupId(CIRCULATION_RULES_UPDATED, 0);
    final String subgroup1 = buildConsumerSubgroupId(CIRCULATION_RULES_UPDATED, 1);

    // first verticle has been deployed beforehand, so we should already see subgroup0 with 1 consumer
    verifyConsumerGroups(Map.of(subgroup0, 1));
    deployVerticle();
    verifyConsumerGroups(Map.of(subgroup0, 1, subgroup1, 1));

    int initialOffsetForSubgroup0 = getOffsetForCirculationRulesUpdateEvents(0);
    int initialOffsetForSubgroup1 = getOffsetForCirculationRulesUpdateEvents(1);

    JsonObject rules = circulationRulesFixture.getRules().getJson();
    publishCirculationRulesUpdateEvent(rules, rules);

    waitForValue(() -> getOffsetForCirculationRulesUpdateEvents(0), initialOffsetForSubgroup0 + 1);
    waitForValue(() -> getOffsetForCirculationRulesUpdateEvents(1), initialOffsetForSubgroup1 + 1);
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
    publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutTenant);
    publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutType);
    publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutTimestamp);
    publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutData);
    publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutOldRules);
    publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutNewRules);
    publishEvent(CIRCULATION_RULES_TOPIC, eventWithoutNewRulesAsText);
    publishEvent(CIRCULATION_RULES_TOPIC, eventWithEmptyNewRulesAsText);
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
    publishEvent(CIRCULATION_RULES_TOPIC, event);
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
    publishEvent(CIRCULATION_RULES_TOPIC, event);
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
    return getOffsetForCirculationRulesUpdateEvents(0);
  }

  private static int getOffsetForCirculationRulesUpdateEvents(int subgroupOrdinal) {
    return getOffset(CIRCULATION_RULES_TOPIC,
      buildConsumerSubgroupId(CIRCULATION_RULES_UPDATED, subgroupOrdinal));
  }

  private static String buildConsumerSubgroupId(DomainEventType eventType, int subgroupOrdinal) {
    return String.format("%s-subgroup-%d", buildConsumerGroupId(eventType), subgroupOrdinal);
  }

  private static String buildConsumerGroupId(DomainEventType eventType) {
    return format("%s.%s-%s", eventType, PomReader.INSTANCE.getModuleName(), PomReader.INSTANCE.getVersion());
  }

  @SneakyThrows
  private static int getOffset(String topic, String consumerGroupId) {
    return waitFor(kafkaAdminClient.listConsumerGroupOffsets(consumerGroupId)
      .map(partitions -> Optional.ofNullable(partitions.get(new TopicPartition(topic, 0)))
        .map(OffsetAndMetadata::getOffset)
        .map(Long::intValue)
        .orElse(0))); // if topic does not exist yet
  }

  private void publishCirculationRulesUpdateEvent(JsonObject oldRules, JsonObject newRules) {
    publishEvent(CIRCULATION_RULES_TOPIC, buildUpdateEvent(oldRules, newRules));
  }

  private void publishEvent(String topic, JsonObject eventPayload) {
    var record = KafkaProducerRecord.create(topic, UUID.randomUUID().toString(), eventPayload);
    record.addHeader("X-Okapi-Tenant", TENANT_ID);
    waitFor(kafkaProducer.write(record));
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

  public static <T> T waitFor(Future<T> future) {
    return waitFor(future, 10);
  }

  @SneakyThrows
  public static <T> T waitFor(Future<T> future, int timeoutSeconds) {
    return future.toCompletionStage()
      .toCompletableFuture()
      .get(timeoutSeconds, SECONDS);
  }

  private Map<String, ConsumerGroupDescription> verifyConsumerGroups(
    Map<String, Integer> groupIdToSize) {

    return waitAtMost(30, SECONDS)
      .until(() -> waitFor(
        kafkaAdminClient.describeConsumerGroups(new ArrayList<>(groupIdToSize.keySet()))),
        groups -> groups.entrySet()
          .stream()
          .collect(toMap(Map.Entry::getKey, e -> e.getValue().getMembers().size()))
          .equals(groupIdToSize)
      );
  }

}
