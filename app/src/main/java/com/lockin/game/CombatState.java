package com.lockin.game;

import com.lockin.game.content.*;
import java.util.List;

/** Allocation-free actor reaction/effect state. Gameplay clock freezes under pause/hitstop. */
public final class CombatState {
  public double stun,knockTime;
  private float knockSpeed;
  private double damage=1,posture=1,damageTime,postureTime;
  private int pendingHeal;
  public double pendingStop,pendingShakeTime,pendingShake;
  public boolean stunned(){return stun>1e-7;}
  public double damageMultiplier(){return damage;}
  public double postureMultiplier(){return posture;}
  public static boolean window(AttackRun run,List<AttackDef.Window> windows) {
    if(!run.running())return false;
    for(int i=0;i<windows.size();i++)if(windows.get(i).includes(run.age()))return true;
    return false;
  }
  public static boolean armored(AttackRun run){return run.definition()!=null && window(run,run.definition().combat.armor);}
  public static boolean damageImmune(AttackRun run){return run.definition()!=null && window(run,run.definition().combat.damageImmunity);}
  public static boolean staggerImmune(AttackRun run){return run.definition()!=null && window(run,run.definition().combat.staggerImmunity);}
  public void start(AttackDef attack) {
    for(var e:attack.combat.effects)switch(e.type()) {
      case "heal":pendingHeal=(int)Math.min(Integer.MAX_VALUE,(long)pendingHeal+(int)e.amount());break;
      case "damageMultiplier":damage=e.amount();damageTime=Math.max(damageTime,e.duration());break;
      case "postureMultiplier":posture=e.amount();postureTime=Math.max(postureTime,e.duration());break;
      case "hitstop":pendingStop=Math.max(pendingStop,e.duration());break;
      case "cameraShake":pendingShake=Math.max(pendingShake,e.amount());pendingShakeTime=Math.max(pendingShakeTime,e.duration());break;
      default:throw new IllegalStateException("Unvalidated combat effect");
    }
  }
  public int drainHeal(){int n=pendingHeal;pendingHeal=0;return n;}
  /** Horizontal speed integrates exactly for remaining duration; vertical impulse is applied once by actor. */
  public float tick(double dt) {
    float dx=(float)(knockSpeed*Math.min(dt,knockTime));
    knockTime=Math.max(0,knockTime-dt);stun=Math.max(0,stun-dt);
    damageTime=Math.max(0,damageTime-dt);if(damageTime==0)damage=1;
    postureTime=Math.max(0,postureTime-dt);if(postureTime==0)posture=1;
    return dx;
  }
  public void clearReaction(){stun=knockTime=0;knockSpeed=0;}
  public void react(CombatRules rules,boolean attackerRight) {
    if(rules.horizontal!=0){knockSpeed=rules.horizontal*(attackerRight?1:-1);knockTime=rules.knockbackDuration;}
    stun=Math.max(stun,rules.stun);
  }
  public void reset(){stun=knockTime=damageTime=postureTime=pendingStop=pendingShakeTime=pendingShake=0;
    damage=posture=1;knockSpeed=0;pendingHeal=0;}
}
