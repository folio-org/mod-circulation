package org.folio.circulation.services.actualcostrecord;

import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_ACTUAL_COST_FEE_TYPE;
import static org.folio.circulation.services.LostItemFeeChargingService.ReferenceDataContext;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordFeeFine;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordIdentifier;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordInstance;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordItem;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordLoan;
import org.folio.circulation.domain.ActualCostRecord.ActualCostRecordUser;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Identifier;
import org.folio.circulation.domain.IdentifierType;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemLossType;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.PatronGroup;
import org.folio.circulation.domain.User;
import org.folio.circulation.infrastructure.storage.ActualCostRecordRepository;
import org.folio.circulation.infrastructure.storage.inventory.IdentifierTypeRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.services.agedtolost.LoanToChargeFees;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;

public class ActualCostRecordService {
  private final ActualCostRecordRepository actualCostRecordRepository;
  private final LocationRepository locationRepository;
  private final IdentifierTypeRepository identifierTypeRepository;
  private final PatronGroupRepository patronGroupRepository;

  public ActualCostRecordService(ActualCostRecordRepository actualCostRecordRepository,
    LocationRepository locationRepository, IdentifierTypeRepository identifierTypeRepository,
    PatronGroupRepository patronGroupRepository) {
    this.actualCostRecordRepository = actualCostRecordRepository;
    this.locationRepository = locationRepository;
    this.identifierTypeRepository = identifierTypeRepository;
    this.patronGroupRepository = patronGroupRepository;
  }

  public CompletableFuture<Result<ReferenceDataContext>> createIfNecessaryForDeclaredLostItem(
    ReferenceDataContext referenceDataContext) {

    Loan loan = referenceDataContext.getLoan();
    FeeFineOwner owner = referenceDataContext.getFeeFineOwner();
    ItemLossType itemLossType = ItemLossType.DECLARED_LOST;
    ZonedDateTime dateOfLoss = ClockUtil.getZonedDateTime();
    FeeFine feeFineType = referenceDataContext.getFeeFines().stream()
      .filter(feeFine -> LOST_ITEM_ACTUAL_COST_FEE_TYPE.equals((feeFine.getFeeFineType())))
      .findFirst()
      .orElse(null);

    return createActualCostRecordIfNecessary(loan, owner, itemLossType, dateOfLoss, feeFineType)
      .thenApply(mapResult(referenceDataContext::withActualCostRecord));
  }

  public CompletableFuture<Result<LoanToChargeFees>> createIfNecessaryForAgedToLostItem(
    LoanToChargeFees loanToChargeFees) {

    Loan loan = loanToChargeFees.getLoan();
    FeeFineOwner owner = loanToChargeFees.getOwner();
    ItemLossType itemLossType = ItemLossType.AGED_TO_LOST;
    ZonedDateTime dateOfLoss = loan.getAgedToLostDateTime();
    Map<String, FeeFine> feeFineTypes = loanToChargeFees.getFeeFineTypes();
    FeeFine feeFineType = feeFineTypes == null ? null : feeFineTypes.get(LOST_ITEM_ACTUAL_COST_FEE_TYPE);

    return createActualCostRecordIfNecessary(loan, owner, itemLossType, dateOfLoss, feeFineType)
      .thenApply(mapResult(loanToChargeFees::withActualCostRecord));
  }

  private CompletableFuture<Result<ActualCostRecord>> createActualCostRecordIfNecessary(
    Loan loan, FeeFineOwner feeFineOwner, ItemLossType itemLossType,
    ZonedDateTime dateOfLoss, FeeFine feeFine) {

    if (!loan.getLostItemPolicy().hasActualCostFee()) {
      return completedFuture(succeeded(null));
    }

    ActualCostRecordContext context = new ActualCostRecordContext()
      .withLossType(itemLossType)
      .withLossDate(dateOfLoss)
      .withLoan(loan)
      .withFeeFineOwner(feeFineOwner)
      .withFeeFine(feeFine);

    return lookupPermanentLocation(context)
      .thenCompose(r -> r.after(this::lookupIdentifierTypes))
      .thenCompose(r -> r.after(this::lookupPatronGroup))
      .thenCompose(r -> r.after(ctx -> actualCostRecordRepository.createActualCostRecord(buildActualCostRecord(ctx))));
  }

  private CompletableFuture<Result<ActualCostRecordContext>> lookupPermanentLocation(
    ActualCostRecordContext context) {

    return locationRepository.getPermanentLocation(context.getLoan().getItem())
      .thenApply(r -> r.map(context::withItemPermanentLocation));
  }

  private CompletableFuture<Result<ActualCostRecordContext>> lookupPatronGroup(
    ActualCostRecordContext context) {

    User user = context.getLoan().getUser();
    if (user.getPatronGroup() != null) {
      return ofAsync(context);
    }

    return patronGroupRepository.findGroupForUser(user)
      .thenApply(r -> r.map(context.getLoan()::withUser))
      .thenApply(r -> r.map(context::withLoan));
  }

  private CompletableFuture<Result<ActualCostRecordContext>> lookupIdentifierTypes(
    ActualCostRecordContext context) {

    return identifierTypeRepository.fetchFor(context.getLoan().getItem())
      .thenApply(r -> r.map(context::withIdentifierTypes))
      .thenApply(r -> r.map(this::buildIdentifiersList));
  }

  private ActualCostRecordContext buildIdentifiersList(ActualCostRecordContext context) {
    return context.withIdentifiers(context.getLoan().getItem().getIdentifiers()
      .map(i -> buildActualCostRecordIdentifier(i, context.getIdentifierTypes()))
      .collect(Collectors.toList()));
  }

  private ActualCostRecordIdentifier buildActualCostRecordIdentifier(Identifier identifier,
    Collection<IdentifierType> identifierTypes) {

    return new ActualCostRecordIdentifier()
      .withIdentifierTypeId(identifier.getIdentifierTypeId())
      .withIdentifierType(identifierTypes.stream()
        .filter(type -> type.getId().equals(identifier.getIdentifierTypeId()))
        .findFirst()
        .map(IdentifierType::getName)
        .orElse(""))
      .withValue(identifier.getValue());
  }

  private ActualCostRecord buildActualCostRecord(ActualCostRecordContext context) {
    User user = context.getLoan().getUser();
    Loan loan = context.getLoan()
      .withItem(context.getLoan().getItem()
        .withPermanentLocation(context.getItemPermanentLocation()));
    Item item = loan.getItem();
    Instance instance = item.getInstance();
    FeeFineOwner feeFineOwner = context.getFeeFineOwner();
    FeeFine feeFine = context.getFeeFine();
    String patronGroup = ofNullable(user.getPatronGroup())
      .map(PatronGroup::getGroup)
      .orElse(null);
    String effectiveLocationName = ofNullable(item.getLocation())
      .map(Location::getName)
      .orElse(null);

    return new ActualCostRecord()
      .withStatus(ActualCostRecord.Status.OPEN)
      .withLossType(context.getLossType())
      .withLossDate(context.getLossDate())
      .withExpirationDate(loan.getLostItemPolicy()
        .calculateFeeFineChargingPeriodExpirationDateTime(context.getLossDate()))
      .withUser(new ActualCostRecordUser()
        .withId(loan.getUserId())
        .withBarcode(user.getBarcode())
        .withFirstName(user.getFirstName())
        .withLastName(user.getLastName())
        .withMiddleName(user.getMiddleName())
        .withPatronGroupId(user.getPatronGroupId())
        .withPatronGroup(patronGroup))
      .withLoan(new ActualCostRecordLoan()
        .withId(loan.getId()))
      .withItem(new ActualCostRecordItem()
        .withId(item.getItemId())
        .withBarcode(item.getBarcode())
        .withMaterialTypeId(item.getMaterialTypeId())
        .withMaterialType(item.getMaterialTypeName())
        .withPermanentLocationId(item.getPermanentLocationId())
        .withPermanentLocation(item.getPermanentLocationName())
        .withEffectiveLocationId(item.getEffectiveLocationId())
        .withEffectiveLocation(effectiveLocationName)
        .withLoanTypeId(item.getLoanTypeId())
        .withLoanType(item.getLoanTypeName())
        .withHoldingsRecordId(item.getHoldingsRecordId())
        .withEffectiveCallNumberComponents(item.getCallNumberComponents())
        .withVolume(item.getVolume())
        .withChronology(item.getChronology())
        .withEnumeration(item.getEnumeration())
        .withDisplaySummary(item.getDisplaySummary())
        .withCopyNumber(item.getCopyNumber()))
      .withInstance(new ActualCostRecordInstance()
        .withId(instance.getId())
        .withTitle(instance.getTitle())
        .withIdentifiers(context.getIdentifiers())
        .withContributors(instance.getContributors()))
      .withFeeFine(new ActualCostRecordFeeFine()
        .withAccountId(null)
        .withOwnerId(feeFineOwner.getId())
        .withOwner(feeFineOwner.getOwner())
        .withTypeId(feeFine == null ? null : feeFine.getId())
        .withType(feeFine == null ? null : feeFine.getFeeFineType()));
  }

  @AllArgsConstructor
  @NoArgsConstructor(force = true)
  @With
  @Getter
  private static class ActualCostRecordContext {
    private final ItemLossType lossType;
    private final ZonedDateTime lossDate;
    private final Loan loan;
    private final FeeFineOwner feeFineOwner;
    private final FeeFine feeFine;
    private final Location itemPermanentLocation;
    private final Collection<IdentifierType> identifierTypes;
    private final List<ActualCostRecordIdentifier> identifiers;
  }
}
