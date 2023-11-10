package org.folio.circulation;

import static api.support.APITestContext.TENANT_ID;
import static api.support.Wait.waitForSize;
import static api.support.Wait.waitForValue;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toMap;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.circulation.EventConsumerVerticle.buildConfig;
import static org.folio.circulation.domain.events.kafka.KafkaEventType.CIRCULATION_RULES_UPDATED;
import static org.folio.kafka.services.KafkaEnvironmentProperties.environment;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.folio.circulation.domain.events.kafka.KafkaEventType;
import org.folio.circulation.rules.cache.CirculationRulesCache;
import org.folio.circulation.support.http.client.Response;
import org.folio.util.pubsub.support.PomReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
  private static final String CIRCULATION_RULES_ID = randomId();
  private static final String CIRCULATION_RULES_TOPIC = buildTopicName("circulation", "rules");
  private static final String CIRCULATION_RULES_UPDATED_CONSUMER_GROUP_ID =
    String.format("CIRCULATION_RULES_UPDATED.%s", buildModuleId());

  @BeforeAll
  public static void setUp() {
  }

//  @AfterAll
//  public static void tearDown() {
//    waitFor(producer.close()
//      .compose(v -> adminClient.close()));
//  }

  @BeforeEach
  public void beforeEach() {
    CirculationRulesCache.getInstance().dropCache();
  }

  @Test
  void circulationRulesUpdateEventConsumerJoinsExistingEmptyConsumerGroup() {
    final String subgroup0 = buildConsumerSubgroupId(CIRCULATION_RULES_UPDATED, 0);
    final String subgroup1 = buildConsumerSubgroupId(CIRCULATION_RULES_UPDATED, 1);
    final String subgroup2 = buildConsumerSubgroupId(CIRCULATION_RULES_UPDATED, 2);

    // first verticle has already been deployed, its consumer is a member of its own subgroup
    verifyConsumerGroups(Map.of(subgroup0, 1));

    // second verticle is deployed, new consumer is created in a separate subgroup1
    String secondDeploymentId = deployVerticle();
    verifyConsumerGroups(Map.of(subgroup0, 1, subgroup1, 1));

    // third verticle is deployed, new consumer is created in a separate subgroup
    deployVerticle();
    verifyConsumerGroups(Map.of(subgroup0, 1, subgroup1, 1, subgroup2, 1));

    // second verticle is undeployed, its consumer is removed, its subgroup is now empty
    undeployVerticle(secondDeploymentId);
    verifyConsumerGroups(Map.of(subgroup0, 1, subgroup1, 0, subgroup2, 1));

    // fourth verticle is deployed, new consumer joins the empty subgroup
    deployVerticle();
    verifyConsumerGroups(Map.of(subgroup0, 1, subgroup1, 1, subgroup2, 1));
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

    checkOutFixture.checkOutByBarcode(item, user); // checks for status 201
  }

  private static int getOffsetForCirculationRulesUpdateEvents() {
    return getOffsetForCirculationRulesUpdateEvents(0);
  }

  private static int getOffsetForCirculationRulesUpdateEvents(int subgroupOrdinal) {
    return getOffset(CIRCULATION_RULES_TOPIC,
      buildConsumerSubgroupId(CIRCULATION_RULES_UPDATED, subgroupOrdinal));
  }

  private static String buildConsumerSubgroupId(KafkaEventType eventType, int subgroupOrdinal) {
    return String.format("%s-subgroup-%d", buildConsumerGroupId(eventType), subgroupOrdinal);
  }

  private static String buildConsumerGroupId(KafkaEventType eventType) {
    return format("%s.%s-%s", eventType, PomReader.INSTANCE.getModuleName(), PomReader.INSTANCE.getVersion());
  }

  private List<String> listConsumerGroupIds() {
    return waitFor(kafkaAdminClient.listConsumerGroups()
      .map(groups -> groups.stream().map(ConsumerGroupListing::getGroupId).toList()));
  }

  private List<MemberDescription> listConsumers(String consumerGroupId) {
    return waitFor(kafkaAdminClient.describeConsumerGroups(List.of(consumerGroupId))
      .map(groups -> groups.values()
        .stream()
        .findFirst()
        .map(ConsumerGroupDescription::getMembers)
        .orElseGet(Collections::emptyList)));
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
      .put("tenant", TENANT_ID)
      .put("type", "UPDATE")
      .put("old", oldVersion)
      .put("new", newVersion);
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

  public static String buildModuleId() {
    return PomReader.INSTANCE.getModuleName() + "-" + PomReader.INSTANCE.getVersion();
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

  private void waitForConsumerGroups(String... consumerGroupIds) {
    assertThat(
      waitForSize(this::listConsumerGroupIds, consumerGroupIds.length),
      hasItems(consumerGroupIds));
  }

  private Collection<MemberDescription> waitForConsumers(String consumerGroupId, int consumerCount) {
    return waitForSize(() -> listConsumers(consumerGroupId), consumerCount);
  }

  private Map<String, ConsumerGroupDescription> verifyConsumerGroups(
    Map<String, Integer> groupIdToSize) {

    return waitAtMost(20, SECONDS)
      .until(() -> waitFor(
        kafkaAdminClient.describeConsumerGroups(new ArrayList<>(groupIdToSize.keySet()))),
        groups -> groups.entrySet()
          .stream()
          .collect(toMap(Map.Entry::getKey, e -> e.getValue().getMembers().size()))
          .equals(groupIdToSize)
      );
  }


}
