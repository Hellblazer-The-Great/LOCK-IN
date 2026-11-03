package com.lockin.game.content;

import java.util.*;

/** Immutable player-only item. Effects are a closed vocabulary, never executable scripts. */
public final class ItemDef {
  public final String id, name, description, icon, category, attack, combo;
  public final int stackLimit, maxHealthBonus, heal;
  public final double damageMultiplier, postureMultiplier, incomingDamageMultiplier, postureRestore;
  public final Map<String, String> animations;

  public ItemDef(Json.Node n, String icon, Map<String,String> animations) {
    n.only("id", "name", "description", "icon", "category", "stackLimit", "attack", "combo",
        "modifiers", "effects", "animations");
    id=n.str("id"); name=n.str("name"); description=n.str("description"); this.icon=icon;
    category=n.str("category");
    if (!Arrays.asList("weapon","armor","accessory","consumable").contains(category))
      throw n.error("category must be weapon, armor, accessory or consumable");
    stackLimit=n.get("stackLimit").integer(1,999);
    attack=n.str("attack",""); combo=n.str("combo","");
    if (!category.equals("weapon") && (!attack.isEmpty() || !combo.isEmpty() || !animations.isEmpty()))
      throw n.error("only weapons may override attacks, combos or animations");
    if (category.equals("weapon") && attack.isEmpty()) throw n.error("weapon requires attack");
    double damage=1, posture=1, incoming=1; int hp=0;
    if(n.has("modifiers")) {
      Json.Node m=n.get("modifiers");
      m.only("damageMultiplier","postureMultiplier","incomingDamageMultiplier","maxHealthBonus");
      damage=m.has("damageMultiplier")?m.get("damageMultiplier").range(0,10):1;
      posture=m.has("postureMultiplier")?m.get("postureMultiplier").range(0,10):1;
      incoming=m.has("incomingDamageMultiplier")?m.get("incomingDamageMultiplier").range(0,10):1;
      hp=m.has("maxHealthBonus")?m.get("maxHealthBonus").integer(0,1000000):0;
    }
    damageMultiplier=damage; postureMultiplier=posture; incomingDamageMultiplier=incoming; maxHealthBonus=hp;
    int healing=0; double restore=0;
    if(n.has("effects")) {
      Json.Node e=n.get("effects"); e.only("heal","postureRestore");
      healing=e.has("heal")?e.get("heal").integer(0,1000000):0;
      restore=e.has("postureRestore")?e.get("postureRestore").range(0,1e9):0;
    }
    heal=healing; postureRestore=restore;
    if(category.equals("consumable")) {
      if(heal==0 && postureRestore==0) throw n.error("consumable needs a supported effect");
      if(damage!=1 || posture!=1 || incoming!=1 || hp!=0) throw n.error("consumables cannot equip modifiers");
    } else if(heal!=0 || postureRestore!=0) throw n.error("only consumables may have use effects");
    this.animations=Collections.unmodifiableMap(new LinkedHashMap<>(animations));
  }
}
