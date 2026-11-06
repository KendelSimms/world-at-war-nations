# Development notes — 0.1.1-alpha

## Design rule: independent by default
The server advances every known faction without player orders. National data is stored in overworld SavedData, so known factions continue their abstract economy while chunks are unloaded. Simulation runs while the server is running; there is no offline catch-up after shutdown.

## Discovery
- Vanilla Villager entities register as VILLAGER citizens.
- Vanilla AbstractIllager entities register as ILLAGER citizens.
- Citizens are grouped by faction kind, dimension, and nearby coordinate region. This is a provisional heuristic, not proper village POI / outpost / territory detection.
- UUIDs are persisted. Death events remove UUIDs from population counts.
- A citizen is discovered only when its entity joins or loads. Distant never-loaded settlements are not discovered yet.

## Simulation
Every 1,200 overworld ticks (one in-game minute at 20 TPS), each known faction with observed citizens receives one economy update. Production, consumption, welfare, and investment decisions are abstract values. Villager and illager economies have different efficiencies and investment priorities.

## Scope honesty
No actual blocks are mined or placed in this alpha. No villagers are assigned jobs or force-fed; no illager population is spawned. Barracks and workshops are national project counters, not physical structures. Diplomacy, alliances, factories, trade routes, and wars are not implemented yet.

## Build environment
The development container used for this pass has Java 21 but no Java 17 JDK, no installed Gradle distribution, and no outbound DNS/network access. Treat the project as source-checked, not build-verified, until a successful Gradle build is produced.
