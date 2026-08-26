package api.loans;

import static api.support.matchers.EventMatchers.isValidNoticeLogRecordEvent;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.PatronNoticeMatcher.hasNoticeProperties;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.fakes.FakeModNotify;

class ImmediatePatronNoticeFormatTests extends APITests {

  private static final UUID EMAIL_TEMPLATE_ID = UUID.fromString("aaa00000-0000-0000-0000-000000000001");
  private static final UUID SMS_TEMPLATE_ID   = UUID.fromString("bbb00000-0000-0000-0000-000000000002");

  private static final String EMAIL_CONTACT_TYPE = "002";
  private static final String SMS_CONTACT_TYPE   = "003";

  @Test
  void singleEmailConfigIsSentRegardlessOfSmsPref() {
    use(new NoticePolicyBuilder()
      .withName("Email-only check-out policy")
      .withLoanNotices(List.of(checkOutConfig(EMAIL_TEMPLATE_ID).withEmailFormat().create())));

    var patron = usersFixture.steve(
      b -> b.withPreferredContactTypeIds(List.of(SMS_CONTACT_TYPE)));
    var item = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(item, patron);
    endPatronSessionClient.endCheckOutSession(patron.getId());

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    assertThat(FakeModNotify.getSentPatronNotices(),
      hasItems(hasEmailNoticeProperties(patron.getId(), EMAIL_TEMPLATE_ID, anything())));
  }

  @Test
  void smsNoticeIsSentWhenSmsPrefAndGroupContainsBothFormats() {
    use(new NoticePolicyBuilder()
      .withName("Email+SMS check-out policy")
      .withLoanNotices(List.of(
        checkOutConfig(EMAIL_TEMPLATE_ID).withEmailFormat().create(),
        checkOutConfig(SMS_TEMPLATE_ID).withSmsFormat().create())));

    var patron = usersFixture.steve(
      b -> b.withPreferredContactTypeIds(List.of(SMS_CONTACT_TYPE)));
    var item = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(item, patron);
    endPatronSessionClient.endCheckOutSession(patron.getId());

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    assertThat(FakeModNotify.getSentPatronNotices(),
      hasItems(hasNoticeProperties(patron.getId(), SMS_TEMPLATE_ID, "sms", "text/plain", anything())));
  }

  @Test
  void emailOnlyIsSentWhenNoPreferenceAndGroupContainsBothFormats() {
    use(new NoticePolicyBuilder()
      .withName("Email+SMS check-out policy")
      .withLoanNotices(List.of(
        checkOutConfig(EMAIL_TEMPLATE_ID).withEmailFormat().create(),
        checkOutConfig(SMS_TEMPLATE_ID).withSmsFormat().create())));

    var patron = usersFixture.steve(); // no preferred contact type
    var item = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(item, patron);
    endPatronSessionClient.endCheckOutSession(patron.getId());

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    assertThat(FakeModNotify.getSentPatronNotices(),
      hasItems(hasEmailNoticeProperties(patron.getId(), EMAIL_TEMPLATE_ID, anything())));
  }

  @Test
  void resolvedTemplateIdAppearsInNoticePayloadAndLog() {
    use(new NoticePolicyBuilder()
      .withName("Email+SMS check-out policy")
      .withLoanNotices(List.of(
        checkOutConfig(EMAIL_TEMPLATE_ID).withEmailFormat().create(),
        checkOutConfig(SMS_TEMPLATE_ID).withSmsFormat().create())));

    var patron = usersFixture.steve(
      b -> b.withPreferredContactTypeIds(List.of(SMS_CONTACT_TYPE)));
    var item = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(item, patron);
    endPatronSessionClient.endCheckOutSession(patron.getId());

    var noticeLogEvents = verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    var sentNotice = FakeModNotify.getFirstSentPatronNotice();
    assertThat(sentNotice,
      hasNoticeProperties(patron.getId(), SMS_TEMPLATE_ID, "sms", "text/plain", anything()));
    assertThat(noticeLogEvents.getFirst(), isValidNoticeLogRecordEvent(sentNotice));
  }

  @Test
  void emailAndSmsNoticesAreSentWhenBothFormatsArePreferred() {
    use(new NoticePolicyBuilder()
      .withName("Email+SMS check-out policy")
      .withLoanNotices(List.of(
        checkOutConfig(EMAIL_TEMPLATE_ID).withEmailFormat().create(),
        checkOutConfig(SMS_TEMPLATE_ID).withSmsFormat().create())));

    var patron = usersFixture.steve(
      b -> b.withPreferredContactTypeIds(List.of(EMAIL_CONTACT_TYPE, SMS_CONTACT_TYPE)));
    var item = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(item, patron);
    endPatronSessionClient.endCheckOutSession(patron.getId());

    verifyNumberOfSentNotices(2);
    verifyNumberOfPublishedEvents(NOTICE, 2);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    var notices = FakeModNotify.getSentPatronNotices();
    assertThat(notices, hasSize(2));
    assertThat(notices, hasItems(
      hasEmailNoticeProperties(patron.getId(), EMAIL_TEMPLATE_ID, anything()),
      hasNoticeProperties(patron.getId(), SMS_TEMPLATE_ID, "sms", "text/plain", anything())));
  }

  @Test
  void noNoticeIsSentWhenPolicyHasNoConfigurationForEventType() {
    use(new NoticePolicyBuilder()
      .withName("No check-out notices policy")
      .withLoanNotices(List.of()));

    var patron = usersFixture.steve();
    var item = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(item, patron);
    endPatronSessionClient.endCheckOutSession(patron.getId());

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void firstEmailConfigWinsWhenPolicyHasDuplicateEmailConfigurations() {
    UUID secondEmailTemplateId = UUID.fromString("ccc00000-0000-0000-0000-000000000003");

    use(new NoticePolicyBuilder()
      .withName("Duplicate email check-out policy")
      .withLoanNotices(List.of(
        checkOutConfig(EMAIL_TEMPLATE_ID).withEmailFormat().create(),
        checkOutConfig(secondEmailTemplateId).withEmailFormat().create())));

    var patron = usersFixture.steve();
    var item = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(item, patron);
    endPatronSessionClient.endCheckOutSession(patron.getId());

    verifyNumberOfSentNotices(1);
    assertThat(FakeModNotify.getSentPatronNotices(),
      hasItems(hasEmailNoticeProperties(patron.getId(), EMAIL_TEMPLATE_ID, anything())));
  }

  @Test
  void singleNoticeIsSentWhenMultipleItemsCheckedOutWithSamePolicy() {
    use(new NoticePolicyBuilder()
      .withName("Email check-out policy")
      .withLoanNotices(List.of(checkOutConfig(EMAIL_TEMPLATE_ID).withEmailFormat().create())));

    var patron = usersFixture.steve();
    var firstItem = itemsFixture.basedUponNod();
    var secondItem = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(firstItem, patron);
    checkOutFixture.checkOutByBarcode(secondItem, patron);
    endPatronSessionClient.endCheckOutSession(patron.getId());

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    assertThat(FakeModNotify.getSentPatronNotices(),
      hasItems(hasEmailNoticeProperties(patron.getId(), EMAIL_TEMPLATE_ID, anything())));
  }

  @Test
  void smsNoticeIsSentWhenOnlyDeprecatedPreferenceIsSet() {
    use(new NoticePolicyBuilder()
      .withName("Email+SMS check-out policy")
      .withLoanNotices(List.of(
        checkOutConfig(EMAIL_TEMPLATE_ID).withEmailFormat().create(),
        checkOutConfig(SMS_TEMPLATE_ID).withSmsFormat().create())));

    var patron = usersFixture.steve(
      b -> b.withDeprecatedPreferredContactTypeId(SMS_CONTACT_TYPE));
    var item = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(item, patron);
    endPatronSessionClient.endCheckOutSession(patron.getId());

    verifyNumberOfSentNotices(1);
    assertThat(FakeModNotify.getSentPatronNotices(),
      hasItems(hasNoticeProperties(patron.getId(), SMS_TEMPLATE_ID, "sms", "text/plain", anything())));
  }

  private static NoticeConfigurationBuilder checkOutConfig(UUID templateId) {
    return new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withCheckOutEvent();
  }
}
