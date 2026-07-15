package com.pixelmc.pixellogic.core.simulation.context;

import com.pixelmc.pixellogic.core.simulation.result.SimulationActionResult;
import com.pixelmc.pixellogic.core.simulation.result.SimulationMessageResult;
import com.pixelmc.pixellogic.core.simulation.result.SimulationStateChangeResult;
import com.pixelmc.pixellogic.core.model.PlayerGameMode;
import com.pixelmc.pixellogic.core.runtime.RuntimeEntityLookup;
import com.pixelmc.pixellogic.core.runtime.RuntimeEntityProvider;
import com.pixelmc.pixellogic.core.runtime.RuntimeOnlinePlayer;
import com.pixelmc.pixellogic.core.runtime.RuntimeOnlinePlayerList;
import com.pixelmc.pixellogic.core.runtime.RuntimeSubjectReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class SimulationContext implements RuntimeEntityProvider {
    private final String runId;
    private final long generation;
    private final SimulationActor actor;
    private final SimulationWorld world;
    private final RuntimeEntityProvider onlinePlayerProvider;
    private final List<SimulationActionResult> actionResults = new ArrayList<>();
    private final List<SimulationMessageResult> messageResults = new ArrayList<>();
    private final List<SimulationStateChangeResult> stateChanges = new ArrayList<>();
    private UUID onlinePlayerFixtureId;
    private RuntimeEntityLookup onlinePlayerFixtureLookup;
    private SimulationActor onlinePlayerFixture;
    private boolean timerScheduled;

    public SimulationContext(
            String runId,
            long generation,
            SimulationActor actor,
            SimulationWorld world
    ) {
        this(runId, generation, actor, world, RuntimeEntityProvider.UNAVAILABLE);
    }

    public SimulationContext(
            String runId,
            long generation,
            SimulationActor actor,
            SimulationWorld world,
            RuntimeEntityProvider onlinePlayerProvider
    ) {
        this.runId = runId == null || runId.isBlank() ? UUID.randomUUID().toString() : runId;
        this.generation = generation;
        this.actor = actor;
        this.world = world;
        this.onlinePlayerProvider = onlinePlayerProvider == null
                ? RuntimeEntityProvider.UNAVAILABLE
                : onlinePlayerProvider;
    }

    public String runId() {
        return runId;
    }

    public long generation() {
        return generation;
    }

    public SimulationActor actor() {
        return actor;
    }

    public SimulationPosition actorPosition() {
        return actor.position();
    }

    public SimulationWorld world() {
        return world;
    }

    public Optional<SimulationEntity> targetEntity() {
        return Optional.ofNullable(world.targetEntity());
    }

    public synchronized Optional<SimulationEntity> entity(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            return Optional.empty();
        }
        if (actor.id().toString().equals(referenceId)) {
            return actor.removed() ? Optional.empty() : Optional.of(actor);
        }
        if (onlinePlayerFixture != null && onlinePlayerFixture.id().toString().equals(referenceId)) {
            return onlinePlayerFixture.removed() ? Optional.empty() : Optional.of(onlinePlayerFixture);
        }
        return targetEntity().filter(entity -> !entity.removed() && entity.id().toString().equals(referenceId));
    }

    @Override
    public RuntimeEntityLookup resolve(RuntimeSubjectReference reference) {
        if (reference == null) {
            return RuntimeEntityLookup.unresolvable();
        }
        Optional<SimulationEntity> resolved = entity(reference.id());
        if (resolved.isEmpty()) {
            return RuntimeEntityLookup.unresolvable();
        }
        SimulationEntity entity = resolved.get();
        if (entity instanceof SimulationActor player && !player.online()) {
            return RuntimeEntityLookup.offline(player.displayName());
        }
        return RuntimeEntityLookup.resolved(entity);
    }

    @Override
    public synchronized RuntimeEntityLookup resolveOnlinePlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return RuntimeEntityLookup.unresolvable();
        }
        if (actor.id().equals(playerUuid)) {
            return actor.online()
                    ? RuntimeEntityLookup.resolved(actor)
                    : RuntimeEntityLookup.offline(actor.displayName());
        }
        if (onlinePlayerFixtureId != null) {
            return onlinePlayerFixtureId.equals(playerUuid)
                    ? onlinePlayerFixtureLookup
                    : RuntimeEntityLookup.unresolvable();
        }
        onlinePlayerFixtureId = playerUuid;
        onlinePlayerFixtureLookup = snapshotOnlinePlayer(playerUuid, onlinePlayerProvider.resolveOnlinePlayer(playerUuid));
        return onlinePlayerFixtureLookup;
    }

    private RuntimeEntityLookup snapshotOnlinePlayer(UUID playerUuid, RuntimeEntityLookup lookup) {
        if (lookup == null) {
            return RuntimeEntityLookup.providerUnavailable();
        }
        if (lookup.status() != RuntimeEntityLookup.Status.RESOLVED) {
            return lookup;
        }
        var entity = lookup.entity();
        RuntimeSubjectReference reference = entity.reference();
        if (reference == null
                || reference.kind() != RuntimeSubjectReference.Kind.PLAYER
                || !playerUuid.toString().equals(reference.id())) {
            return RuntimeEntityLookup.unresolvable();
        }
        if (!entity.online()) {
            return RuntimeEntityLookup.offline(lookup.displayName());
        }
        onlinePlayerFixture = new SimulationActor(
                playerUuid,
                lookup.displayName(),
                true,
                false,
                entity.tags(),
                SimulationPosition.overworldSpawn(),
                entity.health(),
                entity.maxHealth(),
                entity.invulnerable(),
                entity.statusEffects(),
                entity.gameMode().orElse(PlayerGameMode.SURVIVAL)
        );
        return RuntimeEntityLookup.resolved(onlinePlayerFixture);
    }

    @Override
    public RuntimeOnlinePlayerList listOnlinePlayers(String query, int limit) {
        if (!actor.online() || limit <= 0) {
            return new RuntimeOnlinePlayerList(true, List.of());
        }
        String needle = query == null ? "" : query.substring(0, Math.min(64, query.length())).toLowerCase(Locale.ROOT);
        if (!actor.displayName().toLowerCase(Locale.ROOT).contains(needle)) {
            return new RuntimeOnlinePlayerList(true, List.of());
        }
        return new RuntimeOnlinePlayerList(
                true,
                List.of(new RuntimeOnlinePlayer(actor.id(), actor.displayName()))
        );
    }

    @Override
    public boolean statusEffectExists(String effectId) {
        return onlinePlayerProvider.statusEffectExists(effectId);
    }

    public synchronized void addActionResult(SimulationActionResult result) {
        actionResults.add(result);
    }

    public synchronized void addMessageResult(SimulationMessageResult result) {
        messageResults.add(result);
    }

    public synchronized void addStateChange(SimulationStateChangeResult result) {
        stateChanges.add(result);
    }

    public synchronized void markTimerScheduled() {
        timerScheduled = true;
    }

    public synchronized boolean timerScheduled() {
        return timerScheduled;
    }

    public synchronized List<SimulationActionResult> actionResults() {
        return List.copyOf(actionResults);
    }

    public synchronized List<SimulationMessageResult> messageResults() {
        return List.copyOf(messageResults);
    }

    public synchronized List<SimulationStateChangeResult> stateChanges() {
        return List.copyOf(stateChanges);
    }
}
