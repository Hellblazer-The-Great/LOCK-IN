package com.lockin.game;

import com.lockin.game.content.*;
import java.util.*;

/** Simulation-thread inventory menu model. Touch commands never mutate actors on the UI thread. */
public final class InventoryMenu {
  public boolean open;
  public int page, selected, slot;
  public String message="";
  public List<String> ids(Inventory inventory) { return new ArrayList<>(inventory.items().keySet()); }
  public ItemDef selected(Game game) {
    List<String> ids=ids(game.player.inventory);
    return selected<0 || selected>=ids.size()?null:game.content.items.get(ids.get(selected));
  }
  public void command(Game game,int command) {
    if(command==4) {
      open=!open; game.setInventoryPaused(open); message=""; return;
    }
    if(!open) return;
    List<String> ids=ids(game.player.inventory);
    if(command>=100 && command<105) {selected=page*5+command-100; slot=0; message="";return;}
    if(command==5 || command==6) {
      page=Math.max(0,Math.min(Math.max(0,(ids.size()-1)/5),page+(command==5?-1:1)));
      selected=page*5;slot=0;message="";return;
    }
    ItemDef item=selected(game); if(item==null) return;
    Inventory inv=game.player.inventory;
    int count=inv.slots(item.category);
    if(command==10) {slot=count==0?0:(slot+1)%count;return;}
    if(count==0) {message="No slots configured for this category";return;}
    boolean success=false;
    if(command==7) success=inv.equip(item.id,slot);
    if(command==8) success=inv.unequip(item.category,slot);
    if(command==9 && item.category.equals("consumable")) success=inv.consume(slot);
    message=success?"Updated":"Unavailable: check ownership, slot, or active combat action";
    selected=Math.min(selected,Math.max(0,inv.items().size()-1));
  }
}
