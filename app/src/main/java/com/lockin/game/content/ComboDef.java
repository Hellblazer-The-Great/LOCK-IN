package com.lockin.game.content;

import java.util.*;

public final class ComboDef {
  public record Followup(String from, String to, double open, double close, double executeAt) {}

  public final String id;
  public final List<String> sequence;
  public final List<Followup> followups;

  public ComboDef(String id, List<String> sequence, List<Followup> followups) {
    this.id = id;
    this.sequence = Collections.unmodifiableList(new ArrayList<>(sequence));
    this.followups = Collections.unmodifiableList(new ArrayList<>(followups));
  }
}
