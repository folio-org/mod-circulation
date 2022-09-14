package org.folio.circulation.services.actualcostrecord;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.FeeFine.LOST_ITEM_ACTUAL_COST_FEE_TYPE;
import static org.folio.circulation.services.LostItemFeeChargingService.ReferenceDataContext;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.ActualCostRecordFeeFine;
import org.folio.circulation.domain.ActualCostRecordIdentifier;
import org.folio.circulation.domain.ActualCostRecordInstance;
import org.folio.circulation.domain.ActualCostRecordItem;
import org.folio.circulation.domain.ActualCostRecordLoan;
import org.folio.circulation.domain.ActualCostRecordUser;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemLossType;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.User;
import org.folio.circulation.infrastructure.storage.ActualCostRecordRepository;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.services.agedtolost.LoanToChargeFees;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

public class ActualCostRecordService {
  private final ActualCostRecordRepository actualCostRecordRepository;
  private final LocationRepository locationRepository;

  public ActualCostRecordService(ActualCostRecordRepository actualCostRecordRepository,
    LocationRepository locationRepository) {
    this.actualCostRecordRepository = actualCostRecordRepository;
    this.locationRepository = locationRepository;
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

    return locationRepository.getPermanentLocation(loan.getItem())
      .thenCompose(r -> r.after(location -> actualCostRecordRepository.createActualCostRecord(
        buildActualCostRecord(loan, feeFineOwner, itemLossType, dateOfLoss, feeFine, location))));
  }

  private ActualCostRecord buildActualCostRecord(Loan loan, FeeFineOwner feeFineOwner,
    ItemLossType itemLossType, ZonedDateTime dateOfLoss, FeeFine feeFine,
    Location itemPermanentLocation) {

    User user = loan.getUser();
    loan = loan.withItem(loan.getItem().withPermanentLocation(itemPermanentLocation));
    Item item = loan.getItem();
    Instance instance = item.getInstance();

    return new ActualCostRecord()
      .withLossType(itemLossType)
      .withLossDate(dateOfLoss)
      .withExpirationDate(loan.getLostItemPolicy()
        .calculateFeeFineChargingPeriodExpirationDateTime(dateOfLoss))
      .withUser(new ActualCostRecordUser()
        .withId(loan.getUserId())
        .withBarcode(user.getBarcode())
        .withFirstName(user.getFirstName())
        .withLastName(user.getLastName())
        .withMiddleName(user.getMiddleName()))
      .withLoan(new ActualCostRecordLoan()
        .withId(loan.getId()))
      .withItem(new ActualCostRecordItem()
        .withId(item.getItemId())
        .withBarcode(item.getBarcode())
        .withMaterialTypeId(item.getMaterialTypeId())
        .withMaterialType(item.getMaterialTypeName())
        .withPermanentLocationId(item.getPermanentLocationId())
        .withPermanentLocation(item.getPermanentLocationName())
        .withLoanTypeId(item.getLoanTypeId())
        .withLoanType(item.getLoanTypeName())
        .withHoldingsRecordId(item.getHoldingsRecordId())
        .withEffectiveCallNumber(item.getCallNumberComponents()))
      .withInstance(new ActualCostRecordInstance()
        .withId(instance.getId())
        .withTitle(instance.getTitle())
        .withIdentifiers(item.getIdentifiers()
          .map(ActualCostRecordIdentifier::fromIdentifier)
          .collect(Collectors.toList())))
      .withFeeFine(new ActualCostRecordFeeFine()
        .withAccountId(null)
        .withOwnerId(feeFineOwner.getId())
        .withOwner(feeFineOwner.getOwner())
        .withTypeId(feeFine == null ? null : feeFine.getId())
        .withType(feeFine == null ? null : feeFine.getFeeFineType()));
  }
}
