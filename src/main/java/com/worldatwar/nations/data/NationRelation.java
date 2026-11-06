package com.worldatwar.nations.data;

import net.minecraft.nbt.CompoundTag;

/** Persistent bilateral diplomacy record. Scores range from -100 (hostile) to +100 (friendly). */
public final class NationRelation {
    private final String firstFactionId;
    private final String secondFactionId;
    private double score;
    private NationRelationStatus status;

    public NationRelation(String firstFactionId, String secondFactionId, double initialScore) {
        this.firstFactionId = firstFactionId;
        this.secondFactionId = secondFactionId;
        this.score = clamp(initialScore);
        this.status = classify(score);
    }

    public String firstFactionId() { return firstFactionId; }
    public String secondFactionId() { return secondFactionId; }
    public double score() { return score; }
    public NationRelationStatus status() { return status; }

    public void adjust(double amount) {
        score = clamp(score + amount);
        status = classify(score);
    }

    private static double clamp(double value) {
        return Math.max(-100.0, Math.min(100.0, value));
    }

    private static NationRelationStatus classify(double score) {
        if (score >= 75) return NationRelationStatus.ALLIANCE;
        if (score >= 45) return NationRelationStatus.TRADE_PACT;
        if (score >= 10) return NationRelationStatus.PEACE;
        if (score > -25) return NationRelationStatus.TENSE;
        if (score > -65) return NationRelationStatus.RIVALRY;
        return NationRelationStatus.WAR;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("First", firstFactionId);
        tag.putString("Second", secondFactionId);
        tag.putDouble("Score", score);
        tag.putString("Status", status.name());
        return tag;
    }

    public static NationRelation load(CompoundTag tag) {
        NationRelation relation = new NationRelation(tag.getString("First"), tag.getString("Second"),
                tag.contains("Score", net.minecraft.nbt.Tag.TAG_DOUBLE) ? tag.getDouble("Score") : 0);
        if (tag.contains("Status", net.minecraft.nbt.Tag.TAG_STRING)) {
            try { relation.status = NationRelationStatus.valueOf(tag.getString("Status")); }
            catch (IllegalArgumentException ignored) { relation.status = classify(relation.score); }
        }
        return relation;
    }
}
