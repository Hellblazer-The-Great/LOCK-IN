package com.lockin.game;

import com.lockin.game.content.*;

/** One deterministic encounter instance. No boss identity or attack names are hardcoded. */
public final class BossEncounter {
  public final BossDef definition;
  public final Enemy actor;
  private final Content content;
  private final boolean[] events;
  private final int[] outstanding;
  private int phase;
  private boolean active, complete;
  private double transition;
  public BossEncounter(BossDef def,Enemy actor,Content content) {
    this.definition=def;this.actor=actor;this.content=content;
    events=new boolean[def.phases.size()];outstanding=new int[def.rewards.size()];reset();
  }
  public boolean active(){return active && !complete;}
  public boolean complete(){return complete;}
  public int phase(){return phase;}
  public boolean transitioning(){return transition>0;}
  public double transitionProgress(){
    double duration=definition.phases.get(phase).duration();
    return duration==0?1:1-transition/duration;
  }
  public String animation(){return transitioning()?definition.phases.get(phase).animation():"";}
  public boolean rewardsPending(){for(int n:outstanding)if(n>0)return true;return false;}
  public void signal(String event) {
    boolean known=false;
    for(int i=1;i<events.length;i++)if(!event.isEmpty() && event.equals(definition.phases.get(i).event())) {events[i]=true;known=true;}
    if(!known)throw new IllegalArgumentException("Unknown encounter event "+definition.id+":"+event);
  }
  public void reset() {
    phase=0;active=complete=false;transition=0;
    java.util.Arrays.fill(events,false);
    for(int i=0;i<outstanding.length;i++)outstanding[i]=definition.rewards.get(i).quantity();
    actor.encounterLocked=true;actor.encounterInvulnerable=true;
  }
  private void enter(int index) {
    phase=index;var p=definition.phases.get(index);
    actor.configurePhase(content.behaviors.get(p.behavior()),p.speedMultiplier(),p.damageMultiplier(),p.maxPosture());
    transition=p.duration();actor.encounterLocked=transition>0;
    actor.encounterInvulnerable=transition>0 && p.invulnerable();
  }
  public void update(Game game,double dt) {
    if(!active) {
      Player p=game.player;
      if(!CollisionManager.overlaps(p.x,p.y,p.x+p.width,p.y+p.height,definition.x,definition.y,
          definition.x+definition.width,definition.y+definition.height))return;
      active=true;enter(0);
    }
    if(actor.defeated()) {
      complete=true;actor.encounterLocked=true;actor.encounterInvulnerable=false;
      game.recordEncounterCompletion(definition.id);
      for(int i=0;i<outstanding.length;i++)if(outstanding[i]>0)
        outstanding[i]-=game.player.inventory.acquire(definition.rewards.get(i).item(),outstanding[i]);
      return;
    }
    if(transition>0) {
      transition=Math.max(0,transition-dt);
      if(transition==0){actor.encounterLocked=false;actor.encounterInvulnerable=false;}
      return;
    }
    if(phase+1<definition.phases.size()) {
      var next=definition.phases.get(phase+1);
      if(actor.healthFraction()<=next.healthBelow()+1e-9 || events[phase+1])enter(phase+1);
    }
  }
}
