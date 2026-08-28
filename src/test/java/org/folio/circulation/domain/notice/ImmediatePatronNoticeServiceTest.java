package org.folio.circulation.domain.notice;

import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;

import java.util.UUID;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.combiner.NoticeContextCombiner;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.rules.CirculationRulesProcessor;
import org.folio.circulation.services.EventPublishingService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import api.support.builders.NoticeConfigurationBuilder;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class ImmediatePatronNoticeServiceTest {

  @Mock private Clients clients;
  @Mock private CollectionResourceClient patronNoticeClient;
  @Mock private CollectionResourceClient patronNoticePoliciesStorageClient;
  @Mock private CirculationRulesProcessor circulationRulesProcessor;
  @Mock private EventPublishingService eventPublishingService;
  @Mock private NoticeContextCombiner noticeContextCombiner;

  private ImmediatePatronNoticeService immediatePatronNoticeService;

  @BeforeEach
  void setUp() {
    when(clients.patronNoticeClient()).thenReturn(patronNoticeClient);
    when(clients.patronNoticePolicesStorageClient()).thenReturn(patronNoticePoliciesStorageClient);
    when(clients.circulationRulesProcessor()).thenReturn(circulationRulesProcessor);
    when(clients.eventPublishingService()).thenReturn(eventPublishingService);

    immediatePatronNoticeService = new ImmediatePatronNoticeService(clients, noticeContextCombiner);
  }

  @Test
  void acceptNoticeEventsReturnsSucceededWithEmptyEventsList() {
    Result<Void> result = immediatePatronNoticeService.acceptNoticeEvents(emptyList()).join();

    assertThat(result.succeeded(), is(true));
    verifyNoInteractions(patronNoticeClient);
  }

  @Test
  void acceptNoticeEventFailsWhenItemOrUserIsNullInPolicyLookup() {
    PatronNoticeEvent event = new PatronNoticeEvent(
      null, null, NoticeEventType.CHECK_OUT,
      new JsonObject(), new NoticeLogContext(), null, UUID.randomUUID().toString()
    );

    Result<Void> result = immediatePatronNoticeService.acceptNoticeEvent(event).join();

    assertThat(result.failed(), is(true));
  }

  @Test
  void acceptNoticeEventSendsNoticeWhenPolicyAndConfigMatch() {
    String policyId = UUID.randomUUID().toString();
    String recipientId = UUID.randomUUID().toString();

    Item item = createDummyItem();
    User user = createDummyUser(recipientId);

    PatronNoticeEvent event = new PatronNoticeEvent(
      item, user, NoticeEventType.CHECK_OUT,
      new JsonObject(), new NoticeLogContext(), null, recipientId
    );

    CirculationRuleMatch ruleMatch = new CirculationRuleMatch(policyId, null);
    when(circulationRulesProcessor.getNoticePolicyAndMatch(any()))
      .thenReturn(completedFuture(Result.succeeded(ruleMatch)));

    JsonObject policyJson = new JsonObject()
      .put("id", policyId)
      .put("name", "Test Notice Policy")
      .put("loanNotices", new JsonArray().add(
        new NoticeConfigurationBuilder()
          .withTemplateId(UUID.randomUUID())
          .withCheckOutEvent()
          .withEmailFormat()
          .withUponAtTiming()
          .create()
      ));

    Response policyResponse = new Response(200, policyJson.encode(), "application/json");
    when(patronNoticePoliciesStorageClient.get(policyId))
      .thenReturn(completedFuture(Result.succeeded(policyResponse)));

    when(noticeContextCombiner.buildCombinedNoticeContext(any())).thenReturn(new JsonObject());
    when(noticeContextCombiner.buildCombinedNoticeLogContext(any())).thenReturn(new NoticeLogContext());

    Response noticePostResponse = new Response(200, "", "application/json");
    when(patronNoticeClient.post(any())).thenReturn(completedFuture(Result.succeeded(noticePostResponse)));
    when(eventPublishingService.publishEvent(any(), any())).thenReturn(completedFuture(true));

    Result<Void> result = immediatePatronNoticeService.acceptNoticeEvent(event).join();

    assertThat(result.succeeded(), is(true));
    verify(patronNoticeClient).post(any());
  }

  @Test
  void acceptNoticeEventSkipsWhenNoMatchingNoticeConfig() {
    String policyId = UUID.randomUUID().toString();
    String recipientId = UUID.randomUUID().toString();

    Item item = createDummyItem();
    User user = createDummyUser(recipientId);

    PatronNoticeEvent event = new PatronNoticeEvent(
      item, user, NoticeEventType.CHECK_IN,
      new JsonObject(), new NoticeLogContext(), null, recipientId
    );

    CirculationRuleMatch ruleMatch = new CirculationRuleMatch(policyId, null);
    when(circulationRulesProcessor.getNoticePolicyAndMatch(any()))
      .thenReturn(completedFuture(Result.succeeded(ruleMatch)));

    JsonObject policyJson = new JsonObject()
      .put("id", policyId)
      .put("name", "Test Notice Policy")
      .put("loanNotices", new JsonArray()); // no notices

    Response policyResponse = new Response(200, policyJson.encode(), "application/json");
    when(patronNoticePoliciesStorageClient.get(policyId))
      .thenReturn(completedFuture(Result.succeeded(policyResponse)));

    when(noticeContextCombiner.buildCombinedNoticeContext(any())).thenReturn(new JsonObject());
    when(noticeContextCombiner.buildCombinedNoticeLogContext(any())).thenReturn(new NoticeLogContext());

    Result<Void> result = immediatePatronNoticeService.acceptNoticeEvent(event).join();

    assertThat(result.succeeded(), is(true));
    verifyNoInteractions(patronNoticeClient);
  }

  @Test
  void singleImmediatePatronNoticeServiceCanBeInstantiated() {
    SingleImmediatePatronNoticeService singleService = new SingleImmediatePatronNoticeService(clients);
    assertThat(singleService, is(notNullValue()));
  }

  private Item createDummyItem() {
    JsonObject itemJson = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("holdingsRecordId", UUID.randomUUID().toString())
      .put("effectiveLocationId", UUID.randomUUID().toString())
      .put("materialTypeId", UUID.randomUUID().toString())
      .put("permanentLoanTypeId", UUID.randomUUID().toString());
    return Item.from(itemJson);
  }

  private User createDummyUser(String userId) {
    JsonObject userJson = new JsonObject()
      .put("id", userId)
      .put("patronGroup", UUID.randomUUID().toString());
    return User.from(userJson);
  }
}
