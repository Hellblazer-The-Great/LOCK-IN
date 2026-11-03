package com.lockin.game;

import com.lockin.game.content.*;
import java.util.*;

/** Run-local player inventory. Mutate only on the simulation thread; snapshots are immutable. */
public final class Inventory {
  private final Content content;
  private final Player owner;
  private final Map<String,Integer> counts=new LinkedHashMap<>();
  private final String[] weapons=new String[1], armor=new String[1], accessories, quick;
  private double damage=1, posture=1, incoming=1;
  private int healthBonus;

  public Inventory(Content content, Player owner) {
    this.content=content; this.owner=owner;
    accessories=new String[content==null?1:content.accessorySlots];
    quick=new String[content==null?2:content.quickSlots];
  }
  public Map<String,Integer> items() { return Collections.unmodifiableMap(new LinkedHashMap<>(counts)); }
  public int count(String id) { Integer n=counts.get(id); return n==null?0:n; }
  public double damageMultiplier(){return damage;}
  public double postureMultiplier(){return posture;}
  public double incomingMultiplier(){return incoming;}
  public int healthBonus(){return healthBonus;}
  public int slots(String category){return array(category).length;}
  private String[] array(String category) {
    switch(category) {
      case "weapon": return weapons;
      case "armor": return armor;
      case "accessory": return accessories;
      case "consumable": return quick;
      default: throw new IllegalArgumentException("Unknown equipment category "+category);
    }
  }
  private ItemDef item(String id) {
    ItemDef i=content==null?null:content.items.get(id);
    if(i==null) throw new IllegalArgumentException("Unknown item "+id);
    return i;
  }
  /** Returns accepted quantity; no overflow or silent loss of an existing stack. */
  public int acquire(String id,int quantity) {
    if(quantity<0) throw new IllegalArgumentException("Negative acquisition");
    ItemDef i=item(id); int accepted=Math.min(quantity,i.stackLimit-count(id));
    if(accepted>0) counts.put(id,count(id)+accepted);
    return accepted;
  }
  public String equipped(String category,int slot) {
    String[] a=array(category); if(slot<0 || slot>=a.length) throw new IllegalArgumentException("Invalid slot");
    return a[slot]==null?"":a[slot];
  }
  /** A unique item type can occupy one slot; quick slots are references, not duplicate stacks. */
  public boolean equip(String id,int slot) {
    ItemDef i=item(id); String[] a=array(i.category);
    if(slot<0 || slot>=a.length || count(id)==0 || !owner.canManageEquipment()) return false;
    for(String existing:a) if(id.equals(existing)) return false;
    a[slot]=id; recompute(); return true;
  }
  public boolean unequip(String category,int slot) {
    String[] a=array(category);
    if(slot<0 || slot>=a.length || a[slot]==null || !owner.canManageEquipment()) return false;
    a[slot]=null; recompute(); return true;
  }
  public boolean consume(int slot) {
    if(slot<0 || slot>=quick.length || quick[slot]==null || !owner.canManageEquipment()) return false;
    ItemDef i=item(quick[slot]); int n=count(i.id);
    if(n==0) return false;
    owner.heal(i.heal); owner.posture.restore(i.postureRestore);
    if(n==1) { counts.remove(i.id); quick[slot]=null; } else counts.put(i.id,n-1);
    return true;
  }
  public String attack(String fallback){return weapons[0]==null?fallback:item(weapons[0]).attack;}
  public String combo(String fallback){return weapons[0]==null?fallback:item(weapons[0]).combo;}
  public String animation(String state,String fallback) {
    if(weapons[0]==null) return fallback;
    String override=item(weapons[0]).animations.get(state); return override==null?fallback:override;
  }
  private void recompute() {
    damage=posture=incoming=1; healthBonus=0;
    for(String category:new String[]{"weapon","armor","accessory"}) for(String id:array(category)) if(id!=null) {
      ItemDef i=item(id); damage*=i.damageMultiplier; posture*=i.postureMultiplier;
      incoming*=i.incomingDamageMultiplier; healthBonus+=i.maxHealthBonus;
    }
    owner.refreshEquipmentHealth();
  }
  public void reset() {
    counts.clear(); Arrays.fill(weapons,null); Arrays.fill(armor,null);
    Arrays.fill(accessories,null); Arrays.fill(quick,null);
    if(content!=null) for(var e:content.initialItems.entrySet()) acquire(e.getKey(),e.getValue());
    recompute();
  }
}
