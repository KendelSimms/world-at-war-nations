package com.worldatwar.nations.command;

import com.worldatwar.nations.data.NationFaction;
import com.worldatwar.nations.data.NationSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.Locale;

public final class NationCommands {
    private NationCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wwn")
                .then(Commands.literal("factions").executes(context -> listFactions(context.getSource())))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> inspectFaction(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("help").executes(context -> help(context.getSource()))));
    }

    private static int listFactions(CommandSourceStack source) {
        var factions = NationSavedData.get(source.getServer()).factions().stream()
                .sorted(Comparator.comparing(NationFaction::name)).toList();
        source.sendSuccess(() -> Component.literal("[World At War: Nations] Registered civilizations: " + factions.size()), false);
        if (factions.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No civilizations discovered yet. Explore naturally generated villages and illager settlements; their NPCs are registered when their chunks load."), false);
            return 0;
        }
        for (NationFaction faction : factions) {
            String line = String.format(Locale.ROOT, "- %s [%s] | citizens: %d | tech: %d | tier: %d | ID: %s",
                    faction.name(), faction.kind().name().toLowerCase(Locale.ROOT), faction.population(),
                    faction.technology(), faction.settlementTier(), faction.id());
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return factions.size();
    }

    private static int inspectFaction(CommandSourceStack source, String id) {
        var optional = NationSavedData.get(source.getServer()).find(id);
        if (optional.isEmpty()) {
            source.sendFailure(Component.literal("No civilization found with ID: " + id + ". Use /wwn factions to list known civilizations."));
            return 0;
        }
        NationFaction f = optional.get();
        source.sendSuccess(() -> Component.literal("=== " + f.name() + " ==="), false);
        source.sendSuccess(() -> Component.literal("Type: " + f.kind() + " | Dimension: " + f.dimension()), false);
        source.sendSuccess(() -> Component.literal("Center: " + f.centerX() + ", " + f.centerZ() + " | Citizens: " + f.population()), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Stockpiles — Food %.1f | Wood %.1f | Stone %.1f | Iron %.1f | Knowledge %.1f",
                f.food(), f.wood(), f.stone(), f.iron(), f.knowledge())), false);
        source.sendSuccess(() -> Component.literal("Government — Strategic policy " + f.policy().name().toLowerCase(Locale.ROOT).replace('_', ' ')), false);
        source.sendSuccess(() -> Component.literal("Development — Technology " + f.technology() + " | Settlement tier " + f.settlementTier()
                + " | Prosperity " + String.format(Locale.ROOT, "%.1f", f.prosperity())
                + " | Stability " + String.format(Locale.ROOT, "%.1f", f.stability())
                + " | Military readiness " + String.format(Locale.ROOT, "%.1f", f.militaryReadiness())), false);
        source.sendSuccess(() -> Component.literal("Projects — Farms " + f.farms() + " | Lumber camps " + f.lumberCamps()
                + " | Quarries " + f.quarries() + " | Workshops " + f.workshops() + " | Barracks " + f.barracks()), false);
        source.sendSuccess(() -> Component.literal("Recent autonomous decisions:"), false);
        for (String decision : f.recentDecisions()) source.sendSuccess(() -> Component.literal(" • " + decision), false);
        return 1;
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("World At War: Nations commands:"), false);
        source.sendSuccess(() -> Component.literal("/wwn factions — list discovered civilizations"), false);
        source.sendSuccess(() -> Component.literal("/wwn inspect <id> — view a civilization's economy and decisions"), false);
        source.sendSuccess(() -> Component.literal("Civilizations simulate autonomously every 60 in-game seconds. No player orders are required."), false);
        return 1;
    }
}
