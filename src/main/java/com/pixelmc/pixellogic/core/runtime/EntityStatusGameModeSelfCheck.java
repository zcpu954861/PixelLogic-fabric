package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.catalog.BlockDefinition;
import com.pixelmc.pixellogic.core.catalog.BuiltInBlockCatalog;
import com.pixelmc.pixellogic.core.graph.GraphCompiler;
import com.pixelmc.pixellogic.core.graph.GraphValidator;
import com.pixelmc.pixellogic.core.model.EdgeDefinition;
import com.pixelmc.pixellogic.core.model.EdgeType;
import com.pixelmc.pixellogic.core.model.EntityStatusEffect;
import com.pixelmc.pixellogic.core.model.EntityTargetRef;
import com.pixelmc.pixellogic.core.model.EntityTargetRequirement;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.PlayerGameMode;
import com.pixelmc.pixellogic.core.model.SlotDefinition;
import com.pixelmc.pixellogic.core.model.StatusEffectUpdatePolicy;
import com.pixelmc.pixellogic.core.simulation.context.SimulationActor;
import com.pixelmc.pixellogic.core.simulation.context.SimulationContext;
import com.pixelmc.pixellogic.core.simulation.context.SimulationEntity;
import com.pixelmc.pixellogic.core.simulation.context.SimulationPosition;
import com.pixelmc.pixellogic.core.simulation.context.SimulationWorld;
import com.pixelmc.pixellogic.core.simulation.executor.SimulationExecutionRegistry;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationExecutionRequest;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationExecutionResult;
import com.pixelmc.pixellogic.core.simulation.runner.SimulationRunner;
import com.pixelmc.pixellogic.core.state.InMemoryStateStore;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;
import com.pixelmc.pixellogic.core.trace.BoundedTraceBuffer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;
import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.run;

public final class EntityStatusGameModeSelfCheck {
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000802");
    private static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000803");
    private static final String EFFECT_ID = "minecraft:speed";
    private static final String TRIGGER = "entity.status.selfcheck";

    private EntityStatusGameModeSelfCheck() {
    }

    public static void main(String[] args) {
        run("entityStatusGameModeSelfCheck", () -> {
            checkCatalogAndValidation();
            checkVanillaUpdate();
            checkReplaceAndFailures();
            checkRemovalAndGameModes();
            checkTargetSourcesAndFailures();
            checkRuntimeFailurePropagation();
            checkSimulationContextsAndCopies();
            checkDoneAndContinuation();
        });
    }

    private static void checkCatalogAndValidation() {
        require(BuiltInBlockCatalog.catalog().blocks().size() == 33, "catalog should contain 33 blocks");
        require(BuiltInBlockCatalog.catalog().categories().size() == 19, "catalog should contain 19 categories");

        BlockDefinition add = block(BuiltInBlockCatalog.ACTION_ENTITY_ADD_STATUS_EFFECT);
        BlockDefinition remove = block(BuiltInBlockCatalog.ACTION_ENTITY_REMOVE_STATUS_EFFECT);
        BlockDefinition gameMode = block(BuiltInBlockCatalog.ACTION_PLAYER_SET_GAME_MODE);
        checkAction(add, "player-entity.status-effects", EntityTargetRequirement.LIVING_ENTITY);
        checkAction(remove, "player-entity.status-effects", EntityTargetRequirement.LIVING_ENTITY);
        checkAction(gameMode, "player-entity.player-settings", EntityTargetRequirement.PLAYER_ONLY);
        require(add.defaultConfig().equals(Map.of(
                        "target", EntityTargetRef.currentEntity().toJson(),
                        "effectId", EFFECT_ID,
                        "durationSeconds", "30",
                        "level", "1",
                        "updatePolicy", "VANILLA_UPDATE",
                        "ambient", "false",
                        "showParticles", "true",
                        "showIcon", "true"
                )), "add-effect defaults should be explicit and stable");
        require(remove.defaultConfig().equals(Map.of(
                        "target", EntityTargetRef.currentEntity().toJson(),
                        "effectId", EFFECT_ID
                )), "remove-effect defaults should be explicit and stable");
        require(gameMode.defaultConfig().equals(Map.of(
                        "target", EntityTargetRef.currentEntity().toJson(),
                        "gameMode", "SURVIVAL"
                )), "game-mode defaults should be explicit and stable");
        require(add.formSchema().stream().map(field -> field.key()).toList().equals(List.of(
                        "target", "effectId", "durationSeconds", "level",
                        "ambient", "showParticles", "showIcon", "updatePolicy"
                )), "add-effect schema should expose only the requested fields");
        require(add.formSchema().stream().filter(field -> field.key().equals("effectId"))
                        .allMatch(field -> field.type().equals("status_effect")),
                "effect id should use the shared status-effect field");
        require(add.formSchema().stream().filter(field -> field.key().equals("updatePolicy"))
                        .flatMap(field -> field.options().stream()).map(option -> option.value()).toList()
                        .equals(List.of("VANILLA_UPDATE", "REPLACE")),
                "effect update policy should be the audited closed list");
        require(gameMode.formSchema().stream().filter(field -> field.key().equals("gameMode"))
                        .flatMap(field -> field.options().stream()).map(option -> option.value()).toList()
                        .equals(List.of("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR")),
                "game mode should expose exactly four modes");

        require(hasIssue(addGraph("bad-effect", "effectId", "speed"), "status_effect_id_invalid"),
                "malformed effect ids should fail validation");
        require(hasIssue(addGraph("bad-duration-zero", "durationSeconds", "0"), "config_number_range")
                        && hasIssue(addGraph("bad-duration-high", "durationSeconds", "1000001"), "config_number_range")
                        && hasIssue(addGraph("bad-duration-decimal", "durationSeconds", "1.5"), "config_number_invalid"),
                "effect duration should be an integer in the declared range");
        require(hasIssue(addGraph("bad-level-zero", "level", "0"), "config_number_range")
                        && hasIssue(addGraph("bad-level-high", "level", "257"), "config_number_range"),
                "effect level should stay in the user-facing 1..256 range");
        require(hasIssue(addGraph("bad-policy", "updatePolicy", "MERGE"), "config_option_invalid"),
                "unknown effect policies should fail validation");
        require(hasIssue(actionGraph(action(
                "bad-mode",
                BuiltInBlockCatalog.ACTION_PLAYER_SET_GAME_MODE,
                Map.of("gameMode", "HARDCORE")
        )), "config_option_invalid"), "unknown game modes should fail validation");
    }

    private static void checkVanillaUpdate() {
        SimulationEntity fresh = entity(Map.of());
        RuntimeActionOutcome added = execute(
                addAction("add-new", EntityTargetRef.targetEntity(), 10, 2, false, true, false, "VANILLA_UPDATE"),
                context(null, fresh.reference()),
                provider(fresh)
        );
        require(added.changed()
                        && added.message().contains("当前可见效果") && added.message().contains("→")
                        && fresh.statusEffect(EFFECT_ID).orElseThrow().equals(effect(200, 1, false, true, false)),
                "add-effect should convert seconds to ticks and user level to amplifier");

        SimulationEntity maximumLevel = entity(Map.of());
        execute(
                addAction("add-level-256", EntityTargetRef.targetEntity(), 10, 256,
                        false, true, true, "VANILLA_UPDATE"),
                context(null, maximumLevel.reference()),
                provider(maximumLevel)
        );
        require(maximumLevel.statusEffect(EFFECT_ID).orElseThrow().amplifier() == 255,
                "user-facing level 256 should convert to amplifier 255");

        assertVanilla("stronger longer", effect(200, 0, false, true, true),
                effect(400, 1, false, true, true), effect(400, 1, false, true, true));
        assertVanilla("stronger shorter", effect(400, 0, false, true, true),
                effect(200, 1, false, true, true), effect(200, 1, false, true, true));
        assertVanilla("weaker longer", effect(200, 1, false, true, true),
                effect(400, 0, false, true, true), effect(200, 1, false, true, true));
        assertVanilla("weaker shorter", effect(400, 1, false, true, true),
                effect(200, 0, false, true, true), effect(400, 1, false, true, true));
        assertVanilla("same level longer", effect(200, 1, false, true, true),
                effect(400, 1, false, true, true), effect(400, 1, false, true, true));
        assertVanilla("same level shorter", effect(400, 1, false, true, true),
                effect(200, 1, false, true, true), effect(400, 1, false, true, true));
        assertVanilla("identical", effect(400, 1, false, true, true),
                effect(400, 1, false, true, true), effect(400, 1, false, true, true));

        assertVanilla("ambient true to false", effect(400, 1, true, true, true),
                effect(400, 1, false, true, true), effect(400, 1, false, true, true));
        assertVanilla("ambient false to true without upgrade", effect(400, 1, false, true, true),
                effect(400, 1, true, true, true), effect(400, 1, false, true, true));
        assertVanilla("ambient follows duration upgrade", effect(200, 1, false, true, true),
                effect(400, 1, true, true, true), effect(400, 1, true, true, true));
        assertVanilla("particles update independently", effect(400, 1, false, true, true),
                effect(200, 1, false, false, true), effect(400, 1, false, false, true));
        assertVanilla("icon updates independently", effect(400, 1, false, true, true),
                effect(200, 1, false, true, false), effect(400, 1, false, true, false));

        assertVanilla("finite to infinite", effect(400, 1, false, true, true),
                effect(EntityStatusEffect.INFINITE_DURATION, 1, false, true, true),
                effect(EntityStatusEffect.INFINITE_DURATION, 1, false, true, true));
        assertVanilla("infinite rejects same-level finite", effect(EntityStatusEffect.INFINITE_DURATION, 1, false, true, true),
                effect(400, 1, false, true, true),
                effect(EntityStatusEffect.INFINITE_DURATION, 1, false, true, true));
        assertVanilla("stronger finite replaces infinite", effect(EntityStatusEffect.INFINITE_DURATION, 0, false, true, true),
                effect(400, 1, false, true, true), effect(400, 1, false, true, true));
        assertVanilla("weaker infinite stays hidden", effect(400, 1, false, true, true),
                effect(EntityStatusEffect.INFINITE_DURATION, 0, false, true, true),
                effect(400, 1, false, true, true));
    }

    private static void checkReplaceAndFailures() {
        EntityStatusEffect current = effect(400, 2, true, false, false);
        SimulationEntity entity = entity(Map.of(EFFECT_ID, current));
        RuntimeActionOutcome identical = execute(
                addAction("replace-identical", EntityTargetRef.targetEntity(), 20, 3,
                        true, false, false, "REPLACE"),
                context(null, entity.reference()),
                provider(entity)
        );
        require(!identical.changed() && entity.statusEffect(EFFECT_ID).orElseThrow().equals(current),
                "identical replacement should be a no-change");
        EntityStatusEffect requested = effect(100, 0, false, true, true);
        RuntimeActionOutcome replaced = execute(
                addAction("replace-different", EntityTargetRef.targetEntity(), 5, 1,
                        false, true, true, "REPLACE"),
                context(null, entity.reference()),
                provider(entity)
        );
        require(replaced.changed()
                        && entity.statusEffect(EFFECT_ID).orElseThrow().equals(requested),
                "replacement should force the exact requested configuration");

        SimulationEntity infinite = entity(Map.of(
                EFFECT_ID, effect(EntityStatusEffect.INFINITE_DURATION, 0, false, true, true)
        ));
        RuntimeActionOutcome finiteReplacement = execute(
                addAction("replace-infinite", EntityTargetRef.targetEntity(), 5, 1,
                        false, true, true, "REPLACE"),
                context(null, infinite.reference()),
                provider(infinite)
        );
        require(finiteReplacement.changed()
                        && infinite.statusEffect(EFFECT_ID).orElseThrow().equals(requested)
                        && !infinite.statusEffect(EFFECT_ID).orElseThrow().infinite(),
                "replacement should replace an infinite effect with the exact finite request");

        SimulationEntity unknown = entity(Map.of());
        expectActionError(
                addAction("unknown-effect", EntityTargetRef.targetEntity(), 10, 1,
                        false, true, true, "VANILLA_UPDATE", "example:not_registered"),
                context(null, unknown.reference()),
                provider(unknown),
                EntityActionErrorCode.STATUS_EFFECT_UNKNOWN
        );
        RejectedStatusEntity rejected = new RejectedStatusEntity();
        expectActionError(
                addAction("rejected-add", EntityTargetRef.targetEntity(), 10, 1,
                        false, true, true, "VANILLA_UPDATE"),
                context(null, rejected.reference()),
                provider(rejected),
                EntityActionErrorCode.STATUS_EFFECT_REJECTED
        );
        expectActionError(
                action("rejected-remove", BuiltInBlockCatalog.ACTION_ENTITY_REMOVE_STATUS_EFFECT,
                        Map.of("target", EntityTargetRef.targetEntity().toJson(), "effectId", EFFECT_ID)),
                context(null, rejected.reference()),
                provider(rejected),
                EntityActionErrorCode.STATUS_EFFECT_REJECTED
        );
        expectActionError(
                addAction("runtime-number", EntityTargetRef.targetEntity(), 10, 1,
                        false, true, true, "VANILLA_UPDATE", EFFECT_ID, Map.of("durationSeconds", "0")),
                context(null, unknown.reference()),
                provider(unknown),
                EntityActionErrorCode.ENTITY_ACTION_INVALID_NUMBER
        );
    }

    private static void checkRemovalAndGameModes() {
        SimulationEntity entity = entity(Map.of(EFFECT_ID, effect(200, 0, false, true, true)));
        NodeDefinition remove = action(
                "remove",
                BuiltInBlockCatalog.ACTION_ENTITY_REMOVE_STATUS_EFFECT,
                Map.of("target", EntityTargetRef.targetEntity().toJson(), "effectId", EFFECT_ID)
        );
        RuntimeActionOutcome removed = execute(remove, context(null, entity.reference()), provider(entity));
        RuntimeActionOutcome absent = execute(remove, context(null, entity.reference()), provider(entity));
        require(removed.changed()
                        && removed.message().contains("当前可见效果") && removed.message().contains("→")
                        && entity.statusEffect(EFFECT_ID).isEmpty()
                        && !absent.changed() && absent.affectedCount() == 1,
                "removing an effect should distinguish present and absent states");

        for (PlayerGameMode requested : PlayerGameMode.values()) {
            PlayerGameMode initial = requested == PlayerGameMode.SURVIVAL
                    ? PlayerGameMode.CREATIVE
                    : PlayerGameMode.SURVIVAL;
            SimulationActor player = actor(ACTOR_ID, initial);
            NodeDefinition setMode = action(
                    "mode-" + requested,
                    BuiltInBlockCatalog.ACTION_PLAYER_SET_GAME_MODE,
                    Map.of("target", EntityTargetRef.currentEntity().toJson(), "gameMode", requested.name())
            );
            RuntimeActionOutcome changed = execute(setMode, context(player.reference(), null), provider(player));
            RuntimeActionOutcome unchanged = execute(setMode, context(player.reference(), null), provider(player));
            require(changed.changed() && player.gameMode().orElseThrow() == requested
                            && !unchanged.changed() && unchanged.affectedCount() == 1,
                    "game-mode action should support and stabilize at " + requested);
        }

        SimulationEntity nonPlayer = entity(Map.of());
        expectTargetError(
                action("non-player", BuiltInBlockCatalog.ACTION_PLAYER_SET_GAME_MODE,
                        Map.of("target", EntityTargetRef.targetEntity().toJson(), "gameMode", "CREATIVE")),
                context(null, nonPlayer.reference()),
                provider(nonPlayer),
                EntityTargetErrorCode.ENTITY_TARGET_TYPE_MISMATCH
        );
        RejectedPlayer rejected = new RejectedPlayer();
        expectActionError(
                action("rejected-mode", BuiltInBlockCatalog.ACTION_PLAYER_SET_GAME_MODE,
                        Map.of("target", EntityTargetRef.currentEntity().toJson(), "gameMode", "CREATIVE")),
                context(rejected.reference(), null),
                provider(rejected),
                EntityActionErrorCode.PLAYER_GAME_MODE_REJECTED
        );

        SimulationEntity plainPlayer = new SimulationEntity(
                OTHER_ID, "minecraft:player", "Plain Player", Set.of()
        );
        RuntimeActionOutcome plainChanged = execute(
                action("plain-player-mode", BuiltInBlockCatalog.ACTION_PLAYER_SET_GAME_MODE,
                        Map.of("target", EntityTargetRef.currentEntity().toJson(), "gameMode", "CREATIVE")),
                context(plainPlayer.reference(), null),
                provider(plainPlayer)
        );
        require(plainChanged.changed() && plainPlayer.gameMode().orElseThrow() == PlayerGameMode.CREATIVE,
                "a plain minecraft:player simulation entity should carry mutable game mode state");
    }

    private static void checkTargetSourcesAndFailures() {
        UUID conditionId = UUID.fromString("00000000-0000-0000-0000-000000000804");
        UUID onlineId = UUID.fromString("00000000-0000-0000-0000-000000000805");
        SimulationActor current = actor(ACTOR_ID, PlayerGameMode.SURVIVAL);
        SimulationEntity condition = new SimulationEntity(
                conditionId, "minecraft:skeleton", "Condition Subject", Set.of()
        );
        SimulationEntity target = entity(Map.of());
        SimulationActor online = actor(onlineId, PlayerGameMode.SURVIVAL);
        RuntimeEntityProvider provider = provider(current, condition, target, online);
        RuntimeExecutionContext context = new RuntimeExecutionContext(
                ACTOR_ID,
                "session",
                target.reference(),
                current.reference(),
                new RuntimeConditionResult("condition", "condition.test", condition.reference(), true, "matched")
        );

        List<Map.Entry<EntityTargetRef, RuntimeEntityAccess>> sources = List.of(
                Map.entry(EntityTargetRef.currentEntity(), current),
                Map.entry(EntityTargetRef.conditionSubject(), condition),
                Map.entry(EntityTargetRef.targetEntity(), target),
                Map.entry(EntityTargetRef.onlinePlayer(onlineId, online.displayName()), online)
        );
        for (int i = 0; i < sources.size(); i++) {
            Map.Entry<EntityTargetRef, RuntimeEntityAccess> source = sources.get(i);
            RuntimeActionOutcome outcome = execute(
                    addAction("source-" + i, source.getKey(), 10, 1,
                            false, true, true, "VANILLA_UPDATE"),
                    context,
                    provider
            );
            require(outcome.changed() && outcome.target().equals(source.getValue().reference())
                            && source.getValue().statusEffect(EFFECT_ID).isPresent(),
                    "entity target source should resolve to the requested entity: " + source.getKey().source());
        }

        SimulationEntity dead = entity(Map.of());
        require(dead.kill(), "dead-target fixture should be killable");
        expectTargetError(
                addAction("dead-target", EntityTargetRef.targetEntity(), 10, 1,
                        false, true, true, "VANILLA_UPDATE"),
                context(null, dead.reference()),
                provider(dead),
                EntityTargetErrorCode.ENTITY_TARGET_NOT_ALIVE
        );

        UUID offlineId = UUID.fromString("00000000-0000-0000-0000-000000000806");
        SimulationActor offline = new SimulationActor(
                offlineId, "Offline Player", false, false, Set.of()
        );
        expectTargetError(
                action("offline-player", BuiltInBlockCatalog.ACTION_PLAYER_SET_GAME_MODE,
                        Map.of(
                                "target", EntityTargetRef.onlinePlayer(offlineId, offline.displayName()).toJson(),
                                "gameMode", "CREATIVE"
                        )),
                context(null, null),
                provider(offline),
                EntityTargetErrorCode.ENTITY_TARGET_OFFLINE
        );
    }

    private static void checkRuntimeFailurePropagation() {
        SimulationActor player = actor(ACTOR_ID, PlayerGameMode.SURVIVAL);
        List<RuntimeActionOutcome> outcomes = new ArrayList<>();
        RuntimeServices services = services(
                provider(player), outcomes, null, new AtomicReference<>(player.reference())
        );
        NodeDefinition add = addAction(
                "unknown-effect-runtime", EntityTargetRef.currentEntity(), 10, 1,
                false, true, true, "VANILLA_UPDATE", "example:not_registered"
        );
        BoundedTraceBuffer traces = new BoundedTraceBuffer(8, 100);
        GraphRuntime runtime = new GraphRuntime(
                new GraphCompiler().compile(actionGraph(add)),
                new InMemoryStateStore(),
                traces,
                services,
                RuntimeLimits.spikeDefaults()
        );
        RuntimeResult result = runtime.start(new TriggerEvent(TRIGGER, "test", ACTOR_ID, "session"));
        require(!result.success()
                        && result.targetError() == null
                        && result.actionError() != null
                        && result.actionError().code() == EntityActionErrorCode.STATUS_EFFECT_UNKNOWN
                        && outcomes.size() == 1
                        && outcomes.getFirst().status() == RuntimeActionOutcome.Status.FAILURE
                        && outcomes.getFirst().kind().equals(BuiltInBlockCatalog.ACTION_ENTITY_ADD_STATUS_EFFECT)
                        && outcomes.getFirst().code().equals(EntityActionErrorCode.STATUS_EFFECT_UNKNOWN.id())
                        && outcomes.getFirst().target().equals(player.reference())
                        && !outcomes.getFirst().changed()
                        && outcomes.getFirst().affectedCount() == 0
                        && traces.get(result.traceId()).orElseThrow().containsMessage(
                        EntityActionErrorCode.STATUS_EFFECT_UNKNOWN.id()),
                "GraphRuntime should preserve the structured status-effect error, failure outcome and trace code");
    }

    private static void checkSimulationContextsAndCopies() {
        SimulationActor actor = new SimulationActor(
                ACTOR_ID,
                "Copy Source",
                true,
                false,
                Set.of(),
                SimulationPosition.overworldSpawn(),
                20,
                20,
                false,
                Map.of(EFFECT_ID, effect(200, 0, false, true, true)),
                PlayerGameMode.SURVIVAL
        );
        SimulationWorld world = SimulationWorld.overworld();
        SimulationContext failClosed = new SimulationContext("fail-closed", 0, actor, world);
        SimulationContext delegated = new SimulationContext("delegated", 0, actor, world, provider());
        require(!RuntimeEntityProvider.UNAVAILABLE.statusEffectExists(EFFECT_ID)
                        && !failClosed.statusEffectExists(EFFECT_ID),
                "providers without a registry must fail closed for status-effect ids");
        require(delegated.statusEffectExists(EFFECT_ID)
                        && !delegated.statusEffectExists("example:not_registered"),
                "simulation context should delegate authoritative status-effect lookup");

        NodeDefinition remove = action(
                "simulation-remove",
                BuiltInBlockCatalog.ACTION_ENTITY_REMOVE_STATUS_EFFECT,
                Map.of("target", EntityTargetRef.currentEntity().toJson(), "effectId", EFFECT_ID)
        );
        NodeDefinition setMode = action(
                "simulation-mode",
                BuiltInBlockCatalog.ACTION_PLAYER_SET_GAME_MODE,
                Map.of("target", EntityTargetRef.currentEntity().toJson(), "gameMode", "CREATIVE")
        );
        GraphDefinition graph = new GraphDefinition(
                "status-simulation-copy",
                List.of(trigger(), remove, setMode),
                List.of(
                        edge("simulation-e1", "start", "started", remove.id(), "input"),
                        edge("simulation-e2", remove.id(), "done", setMode.id(), "input")
                ),
                Map.of(TRIGGER, "start")
        );
        SimulationRunner runner = new SimulationRunner(
                runtimeServices -> runtime(graph, runtimeServices),
                services(provider(), new ArrayList<>(), null, null),
                SimulationExecutionRegistry.playerTags()
        );
        SimulationExecutionResult result = runner.run(new SimulationExecutionRequest(
                graph.id(), TRIGGER, "test", actor, world, "session", 0L
        ));
        require(result.success() && result.actionResults().size() == 2
                        && result.actionResults().stream().allMatch(action -> action.outcome() != null
                        && action.outcome().changed())
                        && actor.statusEffect(EFFECT_ID).isPresent()
                        && actor.gameMode().orElseThrow() == PlayerGameMode.SURVIVAL,
                "simulation runner should execute against a copied effect/game-mode fixture");
    }

    private static void checkDoneAndContinuation() {
        SimulationActor player = actor(ACTOR_ID, PlayerGameMode.SURVIVAL);
        List<RuntimeActionOutcome> outcomes = new ArrayList<>();
        RuntimeServices services = services(
                provider(player), outcomes, null, new AtomicReference<>(player.reference())
        );
        NodeDefinition add = addAction("add", EntityTargetRef.currentEntity(), 10, 1,
                false, true, true, "VANILLA_UPDATE");
        NodeDefinition remove = action(
                "remove-after-add",
                BuiltInBlockCatalog.ACTION_ENTITY_REMOVE_STATUS_EFFECT,
                Map.of("target", EntityTargetRef.currentEntity().toJson(), "effectId", EFFECT_ID)
        );
        GraphDefinition graph = new GraphDefinition(
                "status-done",
                List.of(trigger(), add, remove),
                List.of(
                        edge("e1", "start", "started", add.id(), "input"),
                        edge("e2", add.id(), "done", remove.id(), "input")
                ),
                Map.of(TRIGGER, "start")
        );
        RuntimeResult result = runtime(graph, services).start(new TriggerEvent(TRIGGER, "test", ACTOR_ID, "session"));
        require(result.success() && outcomes.size() == 2 && player.statusEffect(EFFECT_ID).isEmpty()
                        && outcomes.get(0).kind().equals(BuiltInBlockCatalog.ACTION_ENTITY_ADD_STATUS_EFFECT)
                        && outcomes.get(1).kind().equals(BuiltInBlockCatalog.ACTION_ENTITY_REMOVE_STATUS_EFFECT),
                "successful status actions should continue through the ordinary done output");

        SimulationActor original = actor(ACTOR_ID, PlayerGameMode.SURVIVAL);
        SimulationActor replacement = actor(OTHER_ID, PlayerGameMode.SURVIVAL);
        AtomicReference<RuntimeSubjectReference> initial = new AtomicReference<>(original.reference());
        AtomicReference<TimerContinuation> continuation = new AtomicReference<>();
        RuntimeServices delayedServices = services(
                provider(original, replacement), new ArrayList<>(), continuation, initial
        );
        NodeDefinition wait = action("wait", BuiltInBlockCatalog.TIMER_WAIT, Map.of("durationSeconds", "1"));
        NodeDefinition setMode = action(
                "mode-after-delay",
                BuiltInBlockCatalog.ACTION_PLAYER_SET_GAME_MODE,
                Map.of("target", EntityTargetRef.currentEntity().toJson(), "gameMode", "ADVENTURE")
        );
        GraphDefinition delayedGraph = new GraphDefinition(
                "status-continuation",
                List.of(trigger(), wait, setMode),
                List.of(
                        edge("delay-e1", "start", "started", wait.id(), "input"),
                        edge("delay-e2", wait.id(), "timer_completed", setMode.id(), "input")
                ),
                Map.of(TRIGGER, "start")
        );
        GraphRuntime runtime = runtime(delayedGraph, delayedServices);
        RuntimeResult waiting = runtime.start(new TriggerEvent(TRIGGER, "test", ACTOR_ID, "session"));
        require(waiting.suspended() && continuation.get() != null
                        && continuation.get().cursor().currentEntity().equals(original.reference()),
                "delay should suspend with the original stable entity identity");
        initial.set(replacement.reference());
        RuntimeResult resumed = runtime.resumeTimer(continuation.get());
        require(resumed.success()
                        && original.gameMode().orElseThrow() == PlayerGameMode.ADVENTURE
                        && replacement.gameMode().orElseThrow() == PlayerGameMode.SURVIVAL,
                "continuation should retain the original stable entity identity");
    }

    private static void assertVanilla(
            String name,
            EntityStatusEffect current,
            EntityStatusEffect incoming,
            EntityStatusEffect expected
    ) {
        SimulationEntity entity = entity(Map.of(EFFECT_ID, current));
        RuntimeEntityAccess.StatusEffectMutation mutation = entity.addStatusEffect(
                incoming,
                StatusEffectUpdatePolicy.VANILLA_UPDATE
        );
        RuntimeEntityAccess.StatusEffectMutation expectedMutation = expected.equals(current)
                ? RuntimeEntityAccess.StatusEffectMutation.UNCHANGED
                : RuntimeEntityAccess.StatusEffectMutation.CHANGED;
        require(mutation == expectedMutation && entity.statusEffect(EFFECT_ID).orElseThrow().equals(expected),
                "vanilla update mismatch: " + name);
    }

    private static void checkAction(
            BlockDefinition block,
            String category,
            EntityTargetRequirement requirement
    ) {
        require(block.categoryId().equals(category) && block.entityTargetRequirement() == requirement,
                "action category/target requirement mismatch: " + block.id());
        require(block.inputSlots().size() == 1 && block.inputSlots().getFirst().id().equals("input")
                        && block.outputSlots().size() == 1 && block.outputSlots().getFirst().id().equals("done"),
                "action should expose ordinary input/done slots: " + block.id());
        require(block.formSchema().stream().filter(field -> field.type().equals("entity_target")).count() == 1,
                "action should reuse the shared entity-target field: " + block.id());
    }

    private static GraphRuntime runtime(GraphDefinition graph, RuntimeServices services) {
        return new GraphRuntime(
                new GraphCompiler().compile(graph),
                new InMemoryStateStore(),
                new BoundedTraceBuffer(8, 100),
                services,
                RuntimeLimits.spikeDefaults()
        );
    }

    private static RuntimeActionOutcome execute(
            NodeDefinition node,
            RuntimeExecutionContext context,
            RuntimeEntityProvider provider
    ) {
        return services(provider, new ArrayList<>(), null, null)
                .executeSimulationNode(node, context).orElseThrow().actionOutcome();
    }

    private static void expectActionError(
            NodeDefinition node,
            RuntimeExecutionContext context,
            RuntimeEntityProvider provider,
            EntityActionErrorCode code
    ) {
        try {
            execute(node, context, provider);
            throw new IllegalStateException("expected action error: " + code);
        } catch (EntityActionException exception) {
            require(exception.error().code() == code, "unexpected action error: " + exception.error());
        }
    }

    private static void expectTargetError(
            NodeDefinition node,
            RuntimeExecutionContext context,
            RuntimeEntityProvider provider,
            EntityTargetErrorCode code
    ) {
        try {
            execute(node, context, provider);
            throw new IllegalStateException("expected target error: " + code);
        } catch (EntityTargetException exception) {
            require(exception.error().code() == code, "unexpected target error: " + exception.error());
        }
    }

    private static RuntimeServices services(
            RuntimeEntityProvider provider,
            List<RuntimeActionOutcome> outcomes,
            AtomicReference<TimerContinuation> continuation,
            AtomicReference<RuntimeSubjectReference> initial
    ) {
        return new RuntimeServices() {
            @Override
            public Optional<RuntimeSubjectReference> initialCurrentEntity(UUID playerId, String sessionId) {
                return Optional.ofNullable(initial == null ? null : initial.get());
            }

            @Override
            public RuntimeEntityProvider entityProvider() {
                return provider;
            }

            @Override
            public void sendPlayerMessage(UUID playerId, String message) {
            }

            @Override
            public void debug(String message) {
            }

            @Override
            public void scheduleTimer(Duration delay, TimerContinuation scheduled) {
                if (continuation != null) {
                    continuation.set(scheduled);
                }
            }

            @Override
            public void recordActionOutcome(String nodeId, RuntimeActionOutcome outcome) {
                outcomes.add(outcome);
            }
        };
    }

    private static RuntimeEntityProvider provider(RuntimeEntityAccess... entities) {
        Map<String, RuntimeEntityAccess> byId = new LinkedHashMap<>();
        for (RuntimeEntityAccess entity : entities) {
            byId.put(entity.reference().id(), entity);
        }
        return new RuntimeEntityProvider() {
            @Override
            public RuntimeEntityLookup resolve(RuntimeSubjectReference reference) {
                RuntimeEntityAccess entity = reference == null ? null : byId.get(reference.id());
                return entity != null && entity.reference().kind() == reference.kind() && !entity.removed()
                        ? RuntimeEntityLookup.resolved(entity)
                        : RuntimeEntityLookup.unresolvable();
            }

            @Override
            public RuntimeEntityLookup resolveOnlinePlayer(UUID playerUuid) {
                RuntimeEntityAccess entity = playerUuid == null ? null : byId.get(playerUuid.toString());
                if (entity == null || entity.reference().kind() != RuntimeSubjectReference.Kind.PLAYER
                        || entity.removed()) {
                    return RuntimeEntityLookup.unresolvable();
                }
                return entity.online()
                        ? RuntimeEntityLookup.resolved(entity)
                        : RuntimeEntityLookup.offline(entity.reference().displayName());
            }

            @Override
            public RuntimeOnlinePlayerList listOnlinePlayers(String query, int limit) {
                return new RuntimeOnlinePlayerList(true, List.of());
            }

            @Override
            public boolean statusEffectExists(String effectId) {
                return EFFECT_ID.equals(effectId);
            }
        };
    }

    private static RuntimeExecutionContext context(
            RuntimeSubjectReference current,
            RuntimeSubjectReference target
    ) {
        return new RuntimeExecutionContext(ACTOR_ID, "session", target, current, null);
    }

    private static SimulationEntity entity(Map<String, EntityStatusEffect> effects) {
        return new SimulationEntity(
                TARGET_ID,
                "minecraft:zombie",
                "Zombie",
                Set.of(),
                true,
                20,
                20,
                false,
                effects
        );
    }

    private static SimulationActor actor(UUID id, PlayerGameMode gameMode) {
        return new SimulationActor(
                id,
                "Player-" + id.toString().substring(id.toString().length() - 3),
                true,
                false,
                Set.of(),
                SimulationPosition.overworldSpawn(),
                20,
                20,
                false,
                Map.of(),
                gameMode
        );
    }

    private static EntityStatusEffect effect(
            int durationTicks,
            int amplifier,
            boolean ambient,
            boolean particles,
            boolean icon
    ) {
        return new EntityStatusEffect(EFFECT_ID, durationTicks, amplifier, ambient, particles, icon);
    }

    private static NodeDefinition addAction(
            String id,
            EntityTargetRef target,
            int seconds,
            int level,
            boolean ambient,
            boolean particles,
            boolean icon,
            String policy
    ) {
        return addAction(id, target, seconds, level, ambient, particles, icon, policy, EFFECT_ID);
    }

    private static NodeDefinition addAction(
            String id,
            EntityTargetRef target,
            int seconds,
            int level,
            boolean ambient,
            boolean particles,
            boolean icon,
            String policy,
            String effectId
    ) {
        return addAction(id, target, seconds, level, ambient, particles, icon, policy, effectId, Map.of());
    }

    private static NodeDefinition addAction(
            String id,
            EntityTargetRef target,
            int seconds,
            int level,
            boolean ambient,
            boolean particles,
            boolean icon,
            String policy,
            String effectId,
            Map<String, String> overrides
    ) {
        Map<String, String> config = new LinkedHashMap<>(Map.of(
                "target", target.toJson(),
                "effectId", effectId,
                "durationSeconds", Integer.toString(seconds),
                "level", Integer.toString(level),
                "updatePolicy", policy,
                "ambient", Boolean.toString(ambient),
                "showParticles", Boolean.toString(particles),
                "showIcon", Boolean.toString(icon)
        ));
        config.putAll(overrides);
        return action(id, BuiltInBlockCatalog.ACTION_ENTITY_ADD_STATUS_EFFECT, config);
    }

    private static GraphDefinition addGraph(String id, String key, String value) {
        return actionGraph(action(id, BuiltInBlockCatalog.ACTION_ENTITY_ADD_STATUS_EFFECT, Map.of(key, value)));
    }

    private static NodeDefinition trigger() {
        return action("start", BuiltInBlockCatalog.TRIGGER_MANUAL_TEST, Map.of());
    }

    private static NodeDefinition action(String id, String blockId, Map<String, String> config) {
        BlockDefinition block = block(blockId);
        List<SlotDefinition> slots = new ArrayList<>(block.inputSlots());
        slots.addAll(block.outputSlots());
        Map<String, String> merged = new LinkedHashMap<>(block.defaultConfig());
        merged.putAll(config);
        return new NodeDefinition(id, block.nodeType(), block.id(), slots, merged);
    }

    private static GraphDefinition actionGraph(NodeDefinition action) {
        return new GraphDefinition(
                "validate-" + action.id(),
                List.of(trigger(), action),
                List.of(edge("edge", "start", "started", action.id(), "input")),
                Map.of(TRIGGER, "start")
        );
    }

    private static EdgeDefinition edge(
            String id,
            String source,
            String sourceSlot,
            String target,
            String targetSlot
    ) {
        return new EdgeDefinition(id, source, sourceSlot, target, targetSlot, EdgeType.CONTROL);
    }

    private static boolean hasIssue(GraphDefinition graph, String code) {
        return new GraphValidator().validate(graph).stream().anyMatch(issue -> issue.code().equals(code));
    }

    private static BlockDefinition block(String id) {
        return BuiltInBlockCatalog.block(id).orElseThrow();
    }

    private static final class RejectedStatusEntity extends SimulationEntity {
        private RejectedStatusEntity() {
            super(TARGET_ID, "minecraft:zombie", "Rejecting Zombie", Set.of());
        }

        @Override
        public StatusEffectMutation addStatusEffect(EntityStatusEffect effect, StatusEffectUpdatePolicy policy) {
            return StatusEffectMutation.REJECTED;
        }

        @Override
        public StatusEffectMutation removeStatusEffect(String effectId) {
            return StatusEffectMutation.REJECTED;
        }
    }

    private static final class RejectedPlayer extends SimulationEntity {
        private RejectedPlayer() {
            super(ACTOR_ID, "minecraft:player", "Rejecting Player", Set.of());
        }

        @Override
        public Optional<PlayerGameMode> gameMode() {
            return Optional.of(PlayerGameMode.SURVIVAL);
        }

        @Override
        public boolean changeGameMode(PlayerGameMode gameMode) {
            return false;
        }
    }
}
