package com.worldatwar.nations.data;

import net.minecraft.core.BlockPos;

public record NationWorksite(String factionId, String dimension, int x, int y, int z, Type type) {
    public enum Type { LUMBER, FARM, MINING, BUILD }

    public boolean contains(BlockPos pos, String currentDimension) {
        if (!dimension.equals(currentDimension)) return false;
        long dx = (long) x - pos.getX();
        long dy = (long) y - pos.getY();
        long dz = (long) z - pos.getZ();
        return dx * dx + dy * dy + dz * dz <= 64L;
    }

    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("Faction", factionId);
        tag.putString("Dimension", dimension);
        tag.putInt("X", x); tag.putInt("Y", y); tag.putInt("Z", z);
        tag.putString("Type", type.name());
        return tag;
    }

    public static NationWorksite load(net.minecraft.nbt.CompoundTag tag) {
        Type type;
        try { type = Type.valueOf(tag.getString("Type")); }
        catch (IllegalArgumentException ignored) { type = Type.LUMBER; }
        return new NationWorksite(tag.getString("Faction"), tag.getString("Dimension"),
                tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"), type);
    }
}