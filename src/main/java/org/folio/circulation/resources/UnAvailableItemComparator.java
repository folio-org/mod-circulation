package org.folio.circulation.resources;

import java.util.Comparator;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;

public class AvailableItemComparator implements Comparator<Item> {

    private String pickupLocationId;

    public AvailableItemComparator(String pickupLocationId) {
      this.pickupLocationId = pickupLocationId;
    }

    @Override
    public int compare(Item item1, Item item2) {
      if (item1 == item2)
        return 0;
      if (item1.getStatus() == ItemStatus.AVAILABLE && item2.getStatus() != ItemStatus.AVAILABLE) {  //if first one is available
        return -1;
      } else if (item1.getStatus() != ItemStatus.AVAILABLE && item2.getStatus()  == ItemStatus.AVAILABLE) { //if second one is available
        return 1;
      }  else if (item1.getStatus() == ItemStatus.AVAILABLE && item2.getStatus() == ItemStatus.AVAILABLE) {  //if both are available
        if (item1.getLocationId() == pickupLocationId && item2.getLocationId() == pickupLocationId) {
          return 0;
        } else {
          if (item1.getLocationId() == pickupLocationId) {
            return -1;
          } else return 1;
        }
      }
      return 0; //when both are not available
    }
}
