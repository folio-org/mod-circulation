package org.folio.circulation.domain.notice.schedule;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.NoticeFormat;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.domain.representations.logs.NoticeLogContextItem;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import api.support.builders.NoticeConfigurationBuilder;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ScheduledNoticeHandlerTest {

  private static final String POLICY_ID = "policy-id";

  @Test
  void shouldSendByPreferenceReturnsTrueWhenPolicyLookupFails() {
    var clients = mock(Clients.class);
    var patronNoticePoliciesStorageClient = mock(CollectionResourceClient.class);
    when(clients.patronNoticePolicesStorageClient())
      .thenReturn(patronNoticePoliciesStorageClient);

    var handler = new TestScheduledNoticeHandler(clients);
    var context = new ScheduledNoticeContext(buildNotice(null))
      .withPatronNoticePolicyId(POLICY_ID);

    when(patronNoticePoliciesStorageClient.get(POLICY_ID))
      .thenReturn(completedFuture(Result.failed(new ServerErrorFailure("policy lookup failed"))));

    var result = handler.shouldSendByPreference(context).join();

    assertThat(result, is(true));
  }

  @Test
  void shouldSendByPreferenceMatchesNonRecurringConfigurationAndCachesPolicy() {
    var clients = mock(Clients.class);
    var patronNoticePoliciesStorageClient = mock(CollectionResourceClient.class);
    when(clients.patronNoticePolicesStorageClient())
      .thenReturn(patronNoticePoliciesStorageClient);

    var handler = new TestScheduledNoticeHandler(clients);
    var templateId = UUID.randomUUID();
    var notice = buildNotice(new ScheduledNoticeConfig(NoticeTiming.UPON_AT, null,
      templateId.toString(), NoticeFormat.EMAIL, false));
    var context = new ScheduledNoticeContext(notice).withPatronNoticePolicyId(POLICY_ID);

    when(patronNoticePoliciesStorageClient.get(POLICY_ID))
      .thenReturn(completedFuture(Result.succeeded(policyResponse(loanNotices(
        emailConfig(templateId, false))))));

    assertThat(handler.shouldSendByPreference(context).join(), is(true));
    assertThat(handler.shouldSendByPreference(context).join(), is(true));
    verify(patronNoticePoliciesStorageClient, times(1)).get(POLICY_ID);
  }

  @Test
  void shouldSendByPreferenceReturnsTrueWhenTimingDoesNotMatch() {
    var templateId = UUID.randomUUID();
    var notice = buildNotice(new ScheduledNoticeConfig(NoticeTiming.BEFORE, null,
      templateId.toString(), NoticeFormat.EMAIL, false));

    assertThat(sendByPreference(notice, loanNotices(emailConfig(templateId, false))), is(true));
  }

  @Test
  void shouldSendByPreferenceReturnsTrueWhenRecurringFlagDoesNotMatch() {
    var templateId = UUID.randomUUID();
    var notice = buildNotice(new ScheduledNoticeConfig(NoticeTiming.UPON_AT, null,
      templateId.toString(), NoticeFormat.EMAIL, false));

    assertThat(sendByPreference(notice, loanNotices(recurringEmailConfig(templateId,
      Period.days(1), false))), is(true));
  }

  @Test
  void shouldSendByPreferenceReturnsTrueWhenRealTimeFlagDoesNotMatch() {
    var templateId = UUID.randomUUID();
    var notice = buildNotice(new ScheduledNoticeConfig(NoticeTiming.UPON_AT, null,
      templateId.toString(), NoticeFormat.EMAIL, false));

    assertThat(sendByPreference(notice, loanNotices(emailConfig(templateId, true))), is(true));
  }

  @Test
  void shouldSendByPreferenceMatchesRecurringConfigurationWithEqualPeriods() {
    var templateId = UUID.randomUUID();
    var notice = buildNotice(new ScheduledNoticeConfig(NoticeTiming.UPON_AT, Period.days(1),
      templateId.toString(), NoticeFormat.EMAIL, false));

    assertThat(sendByPreference(notice, loanNotices(recurringEmailConfig(templateId,
      Period.days(1), false))), is(true));
  }

  @Test
  void shouldSendByPreferenceReturnsTrueWhenRecurringPeriodsDiffer() {
    var templateId = UUID.randomUUID();
    var notice = buildNotice(new ScheduledNoticeConfig(NoticeTiming.UPON_AT, Period.days(2),
      templateId.toString(), NoticeFormat.EMAIL, false));

    assertThat(sendByPreference(notice, loanNotices(recurringEmailConfig(templateId,
      Period.days(1), false))), is(true));
  }

  @Test
  void shouldSendByPreferenceReturnsTrueWhenTemplateIsNoLongerInPolicy() {
    var notice = buildNotice(new ScheduledNoticeConfig(NoticeTiming.UPON_AT, null,
      UUID.randomUUID().toString(), NoticeFormat.EMAIL, false));

    assertThat(sendByPreference(notice, loanNotices(emailConfig(UUID.randomUUID(), false))),
      is(true));
  }

  @Test
  void shouldSendByPreferenceSkipsWhenFormatIsNotResolvedFromRequestUserPreferences() {
    var templateId = UUID.randomUUID();
    var notice = buildNotice(new ScheduledNoticeConfig(NoticeTiming.UPON_AT, null,
      templateId.toString(), NoticeFormat.EMAIL, false));

    var context = new ScheduledNoticeContext(notice)
      .withPatronNoticePolicyId(POLICY_ID)
      .withRequest(requestWithRequesterPreferredContactType("003"));

    var loanNotices = new JsonArray()
      .add(emailConfig(templateId, false))
      .add(smsConfig(UUID.randomUUID(), false));

    assertThat(sendByPreference(context, loanNotices), is(false));
  }

  private boolean sendByPreference(ScheduledNotice notice, JsonArray loanNotices) {
    return sendByPreference(new ScheduledNoticeContext(notice)
      .withPatronNoticePolicyId(POLICY_ID), loanNotices);
  }

  private boolean sendByPreference(ScheduledNoticeContext context, JsonArray loanNotices) {
    var clients = mock(Clients.class);
    var patronNoticePoliciesStorageClient = mock(CollectionResourceClient.class);
    when(clients.patronNoticePolicesStorageClient())
      .thenReturn(patronNoticePoliciesStorageClient);

    var handler = new TestScheduledNoticeHandler(clients);

    when(patronNoticePoliciesStorageClient.get(POLICY_ID))
      .thenReturn(completedFuture(Result.succeeded(policyResponse(loanNotices))));

    return handler.shouldSendByPreference(context).join();
  }

  private static Response policyResponse(JsonArray loanNotices) {
    var policyJson = new JsonObject()
      .put("id", POLICY_ID)
      .put("name", "Test Notice Policy")
      .put("loanNotices", loanNotices);

    return new Response(200, policyJson.encode(), "application/json");
  }

  private static JsonArray loanNotices(JsonObject... notices) {
    return new JsonArray(java.util.List.of(notices));
  }

  private static JsonObject emailConfig(UUID templateId, boolean sendInRealTime) {
    return new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withEmailFormat()
      .withUponAtTiming()
      .sendInRealTime(sendInRealTime)
      .create();
  }

  private static JsonObject smsConfig(UUID templateId, boolean sendInRealTime) {
    return new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withSmsFormat()
      .withUponAtTiming()
      .sendInRealTime(sendInRealTime)
      .create();
  }

  private static JsonObject recurringEmailConfig(UUID templateId, Period recurringPeriod,
    boolean sendInRealTime) {

    return new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withDueDateEvent()
      .withEmailFormat()
      .withUponAtTiming()
      .recurring(recurringPeriod)
      .sendInRealTime(sendInRealTime)
      .create();
  }

  private static Request requestWithRequesterPreferredContactType(String contactTypeId) {
    var userJson = new JsonObject()
      .put("personal", new JsonObject()
        .put("preferredContactTypeIds", new JsonArray().add(contactTypeId)));
    var requester = User.from(userJson);

    return Request.from(new JsonObject().put("id", UUID.randomUUID().toString()))
      .withRequester(requester);
  }

  private static ScheduledNotice buildNotice(ScheduledNoticeConfig config) {
    return new ScheduledNotice(
      UUID.randomUUID().toString(), null, null, null, null, null,
      TriggeringEvent.DUE_DATE, null, config);
  }

  private static final class TestScheduledNoticeHandler extends ScheduledNoticeHandler {
    private TestScheduledNoticeHandler(Clients clients) {
      super(clients, new LoanRepository(clients,
        mock(ItemRepository.class), mock(UserRepository.class)));
    }

    @Override
    protected CompletableFuture<Result<ScheduledNoticeContext>> fetchData(
      ScheduledNoticeContext context) {

      return completedFuture(Result.succeeded(context));
    }

    @Override
    protected CompletableFuture<Result<ScheduledNotice>> updateNotice(
      ScheduledNoticeContext context) {

      return completedFuture(Result.succeeded(context.getNotice()));
    }

    @Override
    protected boolean isNoticeIrrelevant(ScheduledNoticeContext context) {
      return false;
    }

    @Override
    protected NoticeLogContext buildNoticeLogContext(ScheduledNoticeContext context) {
      return NoticeLogContext.from(context.getNotice());
    }

    @Override
    protected NoticeLogContextItem buildNoticeLogContextItem(ScheduledNoticeContext context) {
      return null;
    }

    @Override
    protected JsonObject buildNoticeContextJson(ScheduledNoticeContext context) {
      return new JsonObject();
    }
  }
}