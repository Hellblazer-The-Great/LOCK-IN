package com.lockin.game.content;

import java.util.*;

/** Validated reusable encounter; coordinates are world pixels, times seconds. */
public final class BossDef {
  public record Phase(double healthBelow,String event,String behavior,float speedMultiplier,
                      double damageMultiplier,double maxPosture,double duration,
                      boolean invulnerable,String animation) {}
  public record Reward(String item,int quantity) {}
  public final String id,name,level,spawn;
  public final float x,y,width,height;
  public final List<Phase> phases;
  public final List<Reward> rewards;
  public BossDef(Json.Node n) {
    n.only("id","name","level","spawn","trigger","phases","rewards");
    id=n.str("id");name=n.str("name");level=n.str("level");spawn=n.str("spawn");
    Json.Node t=n.get("trigger");t.only("x","y","width","height");
    x=t.f("x",0,32768);y=t.f("y",0,32768);
    width=t.f("width",1,32768);height=t.f("height",1,32768);
    List<Phase> ps=new ArrayList<>();double prior=1.00001;
    for(Json.Node p:n.get("phases").list()) {
      p.only("healthBelow","event","behavior","speedMultiplier","damageMultiplier","maxPosture",
          "transitionDuration","invulnerable","animation");
      double threshold=p.get("healthBelow").range(0,1);
      if(threshold>=prior) throw p.error("healthBelow thresholds must strictly decrease");
      if(ps.isEmpty() && threshold!=1) throw p.error("first phase healthBelow must be 1");
      prior=threshold;
      ps.add(new Phase(threshold,p.str("event",""),p.str("behavior"),
          p.has("speedMultiplier")?p.f("speedMultiplier",0,10):1,
          p.has("damageMultiplier")?p.get("damageMultiplier").range(0,10):1,
          p.get("maxPosture").range(1,1e9),p.get("transitionDuration").range(0,60),
          p.bool("invulnerable",false),p.str("animation","")));
    }
    if(ps.isEmpty() || ps.size()>16) throw n.error("requires 1..16 phases");
    phases=Collections.unmodifiableList(ps);
    List<Reward> rs=new ArrayList<>();Set<String> ids=new HashSet<>();
    for(Json.Node p:n.get("rewards").list()) {
      p.only("item","quantity");String item=p.str("item");
      if(!ids.add(item))throw p.error("duplicate reward item");
      rs.add(new Reward(item,p.get("quantity").integer(1,999)));
    }
    if(rs.size()>16)throw n.error("max 16 rewards");
    rewards=Collections.unmodifiableList(rs);
  }
}
