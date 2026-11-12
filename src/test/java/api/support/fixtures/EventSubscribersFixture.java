package api.support.fixtures;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.APITestContext.TENANT_ID;
import static api.support.Wait.waitForValue;
import static org.folio.circulation.EventConsumerVerticle.genericConsumerGroupId;
import static org.folio.circulation.domain.EventType.FEE_FINE_BALANCE_CHANGED;
import static org.folio.circulation.domain.EventType.LOAN_RELATED_FEE_FINE_CLOSED;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.util.Map;
import java.util.UUID;

import org.folio.circulation.domain.events.FeeFineKafkaTopic;
import org.folio.circulation.support.http.OkapiHeader;

import api.support.KafkaTestHelper;
import io.vertx.core.json.JsonObject;

public class EventSubscribersFixture {
  private final KafkaTestHelper kafkaHelper = KafkaTestHelper.getInstance();
  private static final String ACTUAL_COST_FEE_FINE_TYPE_ID = "73785370-d3bd-4d92-942d-ae2268e02ded";

  public void publishLoanRelatedFeeFineClosedEvent(UUID loanId) {
    publishLoanRelatedFeeFineClosedEvent(loanId, UUID.randomUUID());
  }

  public void publishLoanRelatedFeeFineClosedEvent(UUID loanId, UUID accountId) {
    publishKafkaEvent(FeeFineKafkaTopic.LOAN_RELATED_FEE_FINE_CLOSED,
      buildLoanRelatedFeeFineClosedPayload(loanId, accountId),
      genericConsumerGroupId(LOAN_RELATED_FEE_FINE_CLOSED));
  }

  public void publishLoanRelatedFeeFineClosedEventForActualCostFee(UUID loanId) {
    publishKafkaEvent(FeeFineKafkaTopic.LOAN_RELATED_FEE_FINE_CLOSED,
      buildLoanRelatedFeeFineClosedPayload(loanId, null),
      genericConsumerGroupId(LOAN_RELATED_FEE_FINE_CLOSED));
  }

  public void publishFeeFineBalanceChangedEvent(UUID loanId, UUID accountId) {
    publishKafkaEvent(FeeFineKafkaTopic.FEE_FINE_BALANCE_CHANGED,
      buildFeeFineBalanceChangedPayload(loanId, accountId),
      genericConsumerGroupId(FEE_FINE_BALANCE_CHANGED));
  }

  public int getLoanRelatedFeeFineClosedConsumerOffset() {
    return kafkaHelper.getOffset(
      FeeFineKafkaTopic.LOAN_RELATED_FEE_FINE_CLOSED.fullTopicName(TENANT_ID),
      genericConsumerGroupId(LOAN_RELATED_FEE_FINE_CLOSED));
  }

  private JsonObject buildLoanRelatedFeeFineClosedPayload(UUID loanId, UUID accountId) {
    final JsonObject payload = new JsonObject();
    write(payload, "feeFineId", accountId);
    write(payload, "loanId", loanId);

    return payload;
  }

  private JsonObject buildFeeFineBalanceChangedPayload(UUID loanId, UUID accountId) {
    final JsonObject payload = new JsonObject();
    write(payload, "feeFineId", accountId);
    write(payload, "loanId", loanId);
    write(payload, "feeFineTypeId", ACTUAL_COST_FEE_FINE_TYPE_ID);

    return payload;
  }

  private void publishKafkaEvent(FeeFineKafkaTopic topic, JsonObject payload,
    String consumerGroupId) {

    String topicName = topic.fullTopicName(TENANT_ID);
    int initialOffset = kafkaHelper.getOffset(topicName, consumerGroupId);

    kafkaHelper.publishEvent(topicName, payload, headers());

    waitForValue(() -> kafkaHelper.getOffset(topicName, consumerGroupId), initialOffset + 1);
  }

  private static Map<String, String> headers() {
    var headers = getOkapiHeadersFromContext();
    return Map.of(
      OkapiHeader.OKAPI_URL, headers.getUrl().toString(),
      OkapiHeader.TENANT, headers.getTenantId(),
      OkapiHeader.TOKEN, headers.getToken(),
      OkapiHeader.USER_ID, headers.getUserId(),
      OkapiHeader.REQUEST_ID, requestId(headers.getRequestId())
    );
  }

  private static String requestId(String requestId) {
    return requestId == null ? UUID.randomUUID().toString() : requestId;
  }
}
