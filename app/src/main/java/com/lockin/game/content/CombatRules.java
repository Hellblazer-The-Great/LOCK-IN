package com.lockin.game.content;

import java.util.*;

/** Closed vocabulary for attack reactions, immunity and owner-on-start effects. */
public final class CombatRules {
  public record Effect(String type,double amount,double duration) {}
  public final float horizontal,vertical;
  public final double knockbackDuration,stun;
  public final List<AttackDef.Window> armor,damageImmunity,staggerImmunity;
  public final List<Effect> effects;
  public static final CombatRules NONE=new CombatRules();
  private CombatRules(){horizontal=vertical=0;knockbackDuration=stun=0;
    armor=damageImmunity=staggerImmunity=Collections.emptyList();effects=Collections.emptyList();}
  public CombatRules(Json.Node n,double duration) {
    n.only("reaction","armor","damageImmunity","staggerImmunity","effects");
    float x=0,y=0;double time=0,stunTime=0;
    if(n.has("reaction")) {
      Json.Node p=n.get("reaction");p.only("horizontal","vertical","knockbackDuration","stun");
      x=p.has("horizontal")?p.f("horizontal",-5000,5000):0;
      y=p.has("vertical")?p.f("vertical",-5000,5000):0;
      time=p.has("knockbackDuration")?p.get("knockbackDuration").range(0,5):.15;
      stunTime=p.has("stun")?p.get("stun").range(0,10):0;
      if(x!=0 && time==0)throw p.error("horizontal knockback requires positive duration");
    }
    horizontal=x;vertical=y;knockbackDuration=time;stun=stunTime;
    armor=windows(n,"armor",duration);damageImmunity=windows(n,"damageImmunity",duration);
    staggerImmunity=windows(n,"staggerImmunity",duration);
    List<Effect> es=new ArrayList<>();Set<String> types=new HashSet<>();
    if(n.has("effects"))for(Json.Node e:n.get("effects").list()) {
      e.only("type","amount","duration");String type=e.str("type");
      if(!types.add(type))throw e.error("duplicate effect type");
      double amount,seconds;
      switch(type) {
        case "heal": amount=e.get("amount").integer(0,1000000);seconds=0;
          if(e.has("duration"))throw e.error("heal is immediate and has no duration");break;
        case "damageMultiplier": case "postureMultiplier":
          amount=e.get("amount").range(0,10);seconds=e.get("duration").range(1.0/120,60);break;
        case "hitstop":
          if(e.has("amount"))throw e.error("hitstop uses duration only");
          amount=0;seconds=e.get("duration").range(1.0/120,.5);break;
        case "cameraShake":amount=e.get("amount").range(0,30);seconds=e.get("duration").range(1.0/120,5);break;
        default:throw e.error("unsupported effect type "+type);
      }
      es.add(new Effect(type,amount,seconds));
    }
    effects=Collections.unmodifiableList(es);
  }
  private static List<AttackDef.Window> windows(Json.Node n,String key,double duration) {
    List<AttackDef.Window> out=new ArrayList<>();double previous=0;
    if(n.has(key))for(Json.Node p:n.get(key).list()) {
      p.only("start","end");double start=p.get("start").range(previous,duration);
      double end=p.get("end").range(start+1.0/120,duration);previous=end;
      if(out.size()==32)throw p.error("max 32 windows");out.add(new AttackDef.Window(start,end));
    }
    return Collections.unmodifiableList(out);
  }
}
