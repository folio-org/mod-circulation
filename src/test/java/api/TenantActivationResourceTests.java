package api;

import static api.support.APITestContext.TENANT_ID;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.List;

import org.folio.circulation.domain.events.AuditKafkaTopic;
import org.folio.circulation.domain.events.CirculationKafkaTopic;
import org.folio.circulation.rules.cache.CirculationRulesCache;
import org.folio.circulation.rules.cache.Rules;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import api.support.APITests;

class TenantActivationResourceTests extends APITests {

  @Test
  void tenantActivationWarmsUpCirculationRulesCache() {
    CirculationRulesCache.getInstance().dropCache();
    assertThat(CirculationRulesCache.getInstance().getRules(TENANT_ID), nullValue());
    Response response = tenantActivationFixture.postTenant();
    assertThat(response, hasStatus(HTTP_CREATED));
    Rules cachedRules = CirculationRulesCache.getInstance().getRules(TENANT_ID);
    assertThat(cachedRules, not(nullValue()));
    assertThat(cachedRules.getRulesAsText(), equalTo(circulationRulesFixture.getCirculationRules()));
  }

  @Test
  void kafkaTopicsAreCreatedAndDeleted() {
    List<String> circulationTopics = Arrays.stream(CirculationKafkaTopic.values())
      .map(topic -> topic.fullTopicName(TENANT_ID))
      .toList();
    String auditTopic = AuditKafkaTopic.LOG_RECORD.fullTopicName(TENANT_ID);

    kafkaHelper.deleteTopics(circulationTopics);
    kafkaHelper.deleteTopics(List.of(auditTopic));

    Response postResponse1 = tenantActivationFixture.postTenant();
    assertThat(postResponse1, hasStatus(HTTP_CREATED));
    kafkaHelper.verifyTopicsExist(circulationTopics);
    kafkaHelper.verifyTopicsDoNotExist(List.of(auditTopic));

    kafkaHelper.createTopic(auditTopic);

    Response deleteResponse = tenantActivationFixture.deleteTenant(true);
    assertThat(deleteResponse, hasStatus(HTTP_NO_CONTENT));
    kafkaHelper.verifyTopicsDoNotExist(circulationTopics);
    kafkaHelper.verifyTopicExists(auditTopic);

    // recreate tenant to avoid breaking other tests
    Response postResponse2 = tenantActivationFixture.postTenant();
    assertThat(postResponse2, hasStatus(HTTP_CREATED));
    kafkaHelper.verifyTopicsExist(circulationTopics);
  }
}
