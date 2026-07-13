package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.catalog.BlockDefinition;
import com.pixelmc.pixellogic.core.catalog.BuiltInBlockCatalog;
import com.pixelmc.pixellogic.core.graph.GraphCompiler;
import com.pixelmc.pixellogic.core.graph.GraphValidator;
import com.pixelmc.pixellogic.core.model.EdgeDefinition;
import com.pixelmc.pixellogic.core.model.EdgeType;
import com.pixelmc.pixellogic.core.model.EntityDamageKind;
import com.pixelmc.pixellogic.core.model.EntityTargetRef;
import com.pixelmc.pixellogic.core.model.GraphDefinition;
import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.model.SlotDefinition;
import com.pixelmc.pixellogic.core.simulation.context.SimulationActor;
import com.pixelmc.pixellogic.core.simulation.context.SimulationEntity;
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

public final class EntityHealthTerminationSelfCheck {
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000702");
    private static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000703");
    private static final String TRIGGER = "entity.health.selfcheck";

    private EntityHealthTerminationSelfCheck() {
    }

    public static void main(String[] args) {
        run("entityHealthTerminationSelfCheck", () -> {
            checkCatalogAndValidation();
            checkDamageAndHealing();
            checkSetHealthKillAndRemove();
            checkRuntimeFailurePropagation();
            checkTargetSources();
            checkContinuationIdentity();
        });
    }

    private static void checkCatalogAndValidation() {
        List<String> ids = List.of(
                BuiltInBlockCatalog.ACTION_ENTITY_DAMAGE,
                BuiltInBlockCatalog.ACTION_ENTITY_HEAL,
                BuiltInBlockCatalog.ACTION_ENTITY_SET_HEALTH,
                BuiltInBlockCatalog.ACTION_ENTITY_KILL,
                BuiltInBlockCatalog.ACTION_ENTITY_REMOVE
        );
        require(BuiltInBlockCatalog.catalog().blocks().size() == 30, "catalog should contain 30 blocks");
        for (String id : ids) {
            BlockDefinition block = block(id);
            require(block.inputSlots().size() == 1
                            && block.outputSlots().size() == 1
                            && block.outputSlots().getFirst().id().equals("done"),
                    "health actions should expose only ordinary input/done control slots: " + id);
            require(block.formSchema().getFirst().type().equals("entity_target"),
                    "health actions should reuse the entity target editor: " + id);
        }
        require(block(BuiltInBlockCatalog.ACTION_ENTITY_DAMAGE).categoryId().equals("player-entity.health-attributes")
                        && block(BuiltInBlockCatalog.ACTION_ENTITY_SET_HEALTH).categoryId().equals("player-entity.health-attributes"),
                "health actions should use the health and attributes category");
        require(block(BuiltInBlockCatalog.ACTION_ENTITY_KILL).categoryId().equals("player-entity.entity-management")
                        && block(BuiltInBlockCatalog.ACTION_ENTITY_REMOVE).categoryId().equals("player-entity.entity-management"),
                "kill/remove should use entity management");
        require(block(BuiltInBlockCatalog.ACTION_ENTITY_DAMAGE).formSchema().stream()
                        .filter(field -> field.key().equals("damageKind"))
                        .flatMap(field -> field.options().stream())
                        .map(option -> option.value())
                        .toList()
                        .equals(List.of("GENERIC", "MAGIC", "FIRE", "FALL", "VOID")),
                "damage kinds should be the audited closed list");

        require(hasIssue(actionGraph(action(
                "bad-nan", BuiltInBlockCatalog.ACTION_ENTITY_DAMAGE,
                Map.of("target", EntityTargetRef.currentEntity().toJson(), "amount", "NaN", "damageKind", "GENERIC")
        )), "config_number_invalid"), "NaN should fail authoritative validation");
        require(hasIssue(actionGraph(action(
                "bad-zero", BuiltInBlockCatalog.ACTION_ENTITY_HEAL,
                Map.of("target", EntityTargetRef.currentEntity().toJson(), "amount", "0")
        )), "config_number_range"), "zero healing should fail authoritative validation");
        require(hasIssue(actionGraph(action(
                "bad-kind", BuiltInBlockCatalog.ACTION_ENTITY_DAMAGE,
                Map.of("target", EntityTargetRef.currentEntity().toJson(), "amount", "1", "damageKind", "DROWN")
        )), "config_option_invalid"), "unknown damage kind should fail authoritative validation");
        require(hasIssue(actionGraph(action(
                "bad-health", BuiltInBlockCatalog.ACTION_ENTITY_SET_HEALTH,
                Map.of("target", EntityTargetRef.currentEntity().toJson(), "health", "-1")
        )), "config_number_range"), "negative health should fail authoritative validation");
        require(hasIssue(actionGraph(action(
                "remove-player-config", BuiltInBlockCatalog.ACTION_ENTITY_REMOVE,
                Map.of("target", EntityTargetRef.onlinePlayer(ACTOR_ID, "Player").toJson())
        )), "entity_remove_player_forbidden"), "statically player-only remove targets should fail validation");
    }

    private static void checkDamageAndHealing() {
        for (EntityDamageKind kind : EntityDamageKind.values()) {
            DamageSpy entity = new DamageSpy(TARGET_ID, 20, false);
            RuntimeActionOutcome outcome = execute(
                    action("damage-" + kind.name(), BuiltInBlockCatalog.ACTION_ENTITY_DAMAGE, Map.of(
                            "target", EntityTargetRef.targetEntity().toJson(),
                            "amount", "2",
                            "damageKind", kind.name()
                    )),
                    context(null, entity.reference(), null),
                    provider(entity)
            );
            require(outcome.status() == RuntimeActionOutcome.Status.SUCCESS
                            && outcome.kind().equals(BuiltInBlockCatalog.ACTION_ENTITY_DAMAGE)
                            && outcome.changed()
                            && outcome.affectedCount() == 1
                            && entity.health() == 18
                            && entity.damageCalls == 1
                            && entity.setHealthCalls == 0
                            && outcome.message().contains("造成 2 点")
                            && outcome.message().contains("服务器已接受")
                            && !outcome.message().contains("未减伤近似"),
                    "damage should call the adapter damage path for " + kind);
        }

        SimulationEntity approximate = entity(TARGET_ID, 4, 20, false);
        RuntimeActionOutcome approximateOutcome = execute(
                action("damage-approximate", BuiltInBlockCatalog.ACTION_ENTITY_DAMAGE, Map.of(
                        "target", EntityTargetRef.targetEntity().toJson(),
                        "amount", "6",
                        "damageKind", "GENERIC"
                )),
                context(null, approximate.reference(), null),
                provider(approximate)
        );
        require(approximate.health() == 0
                        && approximateOutcome.message().contains("模拟未减伤近似")
                        && approximateOutcome.message().contains("请求对实体 Zombie 造成 6 点")
                        && approximateOutcome.message().contains("应用 4 点"),
                "simulation damage should identify its deterministic unmitigated approximation");

        SimulationEntity invulnerable = entity(TARGET_ID, 20, 20, true);
        expectActionError(
                action("rejected", BuiltInBlockCatalog.ACTION_ENTITY_DAMAGE, Map.of(
                        "target", EntityTargetRef.targetEntity().toJson(),
                        "amount", "2",
                        "damageKind", "GENERIC"
                )),
                context(null, invulnerable.reference(), null),
                provider(invulnerable),
                EntityActionErrorCode.ENTITY_DAMAGE_REJECTED
        );

        SimulationEntity healing = entity(TARGET_ID, 18, 20, false);
        RuntimeActionOutcome healed = execute(
                action("heal", BuiltInBlockCatalog.ACTION_ENTITY_HEAL, Map.of(
                        "target", EntityTargetRef.targetEntity().toJson(), "amount", "6"
                )),
                context(null, healing.reference(), null),
                provider(healing)
        );
        RuntimeActionOutcome unchanged = execute(
                action("heal-again", BuiltInBlockCatalog.ACTION_ENTITY_HEAL, Map.of(
                        "target", EntityTargetRef.targetEntity().toJson(), "amount", "6"
                )),
                context(null, healing.reference(), null),
                provider(healing)
        );
        require(healed.changed() && healing.health() == 20
                        && healed.message().contains("实际恢复 2 点")
                        && !unchanged.changed() && unchanged.affectedCount() == 1
                        && unchanged.message().contains("实际恢复 0 点"),
                "healing should clamp to maximum and report max-health no-change");

        SimulationEntity decimalHealth = entity(TARGET_ID, 10, 20, false);
        RuntimeActionOutcome decimalHeal = execute(
                action("heal-readable-decimal", BuiltInBlockCatalog.ACTION_ENTITY_HEAL, Map.of(
                        "target", EntityTargetRef.targetEntity().toJson(), "amount", "0.1"
                )),
                context(null, decimalHealth.reference(), null),
                provider(decimalHealth)
        );
        require(decimalHeal.message().contains("实际恢复 0.1 点")
                        && decimalHeal.message().contains("生命值 10 → 10.1"),
                "observed health values should remain readable at runtime float precision");

        SimulationEntity highHealth = entity(TARGET_ID, 999_999, 1_000_000, false);
        RuntimeActionOutcome roundedHeal = execute(
                action("heal-runtime-precision", BuiltInBlockCatalog.ACTION_ENTITY_HEAL, Map.of(
                        "target", EntityTargetRef.targetEntity().toJson(), "amount", "0.001"
                )),
                context(null, highHealth.reference(), null),
                provider(highHealth)
        );
        require(!roundedHeal.changed() && highHealth.health() == 999_999
                        && roundedHeal.message().contains("实际恢复 0 点"),
                "simulation healing should use the target runtime float precision");

        SimulationEntity nonLiving = new SimulationEntity(
                TARGET_ID, "minecraft:item", "Item", Set.of(), false, 20, 20, false
        );
        expectTargetError(
                action("wrong-type", BuiltInBlockCatalog.ACTION_ENTITY_DAMAGE, Map.of(
                        "target", EntityTargetRef.targetEntity().toJson(), "amount", "1", "damageKind", "GENERIC"
                )),
                context(null, nonLiving.reference(), null),
                provider(nonLiving),
                EntityTargetErrorCode.ENTITY_TARGET_TYPE_MISMATCH
        );
        SimulationEntity dead = entity(TARGET_ID, 0, 20, false);
        expectTargetError(
                action("dead", BuiltInBlockCatalog.ACTION_ENTITY_HEAL, Map.of(
                        "target", EntityTargetRef.targetEntity().toJson(), "amount", "1"
                )),
                context(null, dead.reference(), null),
                provider(dead),
                EntityTargetErrorCode.ENTITY_TARGET_NOT_ALIVE
        );
    }

    private static void checkSetHealthKillAndRemove() {
        SimulationEntity target = entity(TARGET_ID, 20, 20, false);
        RuntimeActionOutcome set = execute(
                action("set", BuiltInBlockCatalog.ACTION_ENTITY_SET_HEALTH, Map.of(
                        "target", EntityTargetRef.targetEntity().toJson(), "health", "10"
                )),
                context(null, target.reference(), null),
                provider(target)
        );
        require(set.changed() && target.health() == 10, "set health should apply an in-range value");
        SimulationEntity precisionTarget = entity(OTHER_ID, 20, 20, false);
        RuntimeActionOutcome roundedSet = execute(
                action("set-runtime-precision", BuiltInBlockCatalog.ACTION_ENTITY_SET_HEALTH, Map.of(
                        "target", EntityTargetRef.targetEntity().toJson(), "health", "19.9999999"
                )),
                context(null, precisionTarget.reference(), null),
                provider(precisionTarget)
        );
        require(!roundedSet.changed() && precisionTarget.health() == 20
                        && roundedSet.message().contains("设为 19.9999999")
                        && roundedSet.message().contains("应用 20"),
                "set health should report requested and applied target-runtime values");
        expectActionError(
                action("rounded-above-max", BuiltInBlockCatalog.ACTION_ENTITY_SET_HEALTH, Map.of(
                        "target", EntityTargetRef.targetEntity().toJson(), "health", "20.0000001"
                )),
                context(null, precisionTarget.reference(), null),
                provider(precisionTarget),
                EntityActionErrorCode.ENTITY_HEALTH_ABOVE_MAXIMUM
        );
        expectActionError(
                action("negative", BuiltInBlockCatalog.ACTION_ENTITY_SET_HEALTH, Map.of(
                        "target", EntityTargetRef.targetEntity().toJson(), "health", "-1"
                )),
                context(null, target.reference(), null),
                provider(target),
                EntityActionErrorCode.ENTITY_ACTION_INVALID_NUMBER
        );
        expectActionError(
                action("above-max", BuiltInBlockCatalog.ACTION_ENTITY_SET_HEALTH, Map.of(
                        "target", EntityTargetRef.targetEntity().toJson(), "health", "21"
                )),
                context(null, target.reference(), null),
                provider(target),
                EntityActionErrorCode.ENTITY_HEALTH_ABOVE_MAXIMUM
        );
        RuntimeActionOutcome zero = execute(
                action("zero", BuiltInBlockCatalog.ACTION_ENTITY_SET_HEALTH, Map.of(
                        "target", EntityTargetRef.targetEntity().toJson(), "health", "0"
                )),
                context(null, target.reference(), null),
                provider(target)
        );
        require(zero.changed() && !target.alive() && !target.killed() && !target.removed(),
                "setting zero should use health-zero semantics without becoming remove or kill");

        SimulationActor player = SimulationActor.player(ACTOR_ID, "Player");
        RuntimeActionOutcome killed = execute(
                action("kill", BuiltInBlockCatalog.ACTION_ENTITY_KILL, Map.of(
                        "target", EntityTargetRef.currentEntity().toJson()
                )),
                context(player.reference(), null, null),
                provider(player)
        );
        require(killed.changed() && player.killed() && !player.removed(),
                "kill should allow players and preserve the distinction from removal");

        SimulationEntity removable = entity(TARGET_ID, 20, 20, false);
        RuntimeActionOutcome removed = execute(
                action("remove", BuiltInBlockCatalog.ACTION_ENTITY_REMOVE, Map.of(
                        "target", EntityTargetRef.targetEntity().toJson()
                )),
                context(null, removable.reference(), null),
                provider(removable)
        );
        require(removed.changed() && removable.removed() && !removable.killed(),
                "remove should discard without normal death semantics");
        expectActionError(
                action("remove-player", BuiltInBlockCatalog.ACTION_ENTITY_REMOVE, Map.of(
                        "target", EntityTargetRef.onlinePlayer(ACTOR_ID, "Player").toJson()
                )),
                context(null, null, null),
                provider(player),
                EntityActionErrorCode.ENTITY_REMOVE_PLAYER_FORBIDDEN
        );
        SimulationEntity targetPlayer = new SimulationEntity(
                TARGET_ID, "minecraft:player", "Target Player", Set.of(), true, 20, 20, false
        );
        expectActionError(
                action("remove-target-player", BuiltInBlockCatalog.ACTION_ENTITY_REMOVE, Map.of(
                        "target", EntityTargetRef.targetEntity().toJson()
                )),
                context(null, targetPlayer.reference(), null),
                provider(targetPlayer),
                EntityActionErrorCode.ENTITY_REMOVE_PLAYER_FORBIDDEN
        );
    }

    private static void checkTargetSources() {
        SimulationActor player = SimulationActor.player(ACTOR_ID, "Player");
        SimulationEntity current = entity(TARGET_ID, 19, 20, false);
        SimulationEntity target = entity(OTHER_ID, 19, 20, false);
        RuntimeEntityProvider provider = provider(player, current, target);
        List<SourceCase> cases = List.of(
                new SourceCase(EntityTargetRef.currentEntity(), context(current.reference(), target.reference(), null), current),
                new SourceCase(
                        EntityTargetRef.conditionSubject(),
                        context(current.reference(), target.reference(), new RuntimeConditionResult(
                                "condition", "condition.entity.has_tag", target.reference(), true, "target"
                        )),
                        target
                ),
                new SourceCase(EntityTargetRef.targetEntity(), context(current.reference(), target.reference(), null), target),
                new SourceCase(EntityTargetRef.onlinePlayer(ACTOR_ID, "Player"), context(null, null, null), player)
        );
        for (SourceCase sourceCase : cases) {
            double before = sourceCase.expected.health();
            RuntimeActionOutcome outcome = execute(
                    action("source-" + sourceCase.target.source(), BuiltInBlockCatalog.ACTION_ENTITY_HEAL, Map.of(
                            "target", sourceCase.target.toJson(), "amount", "1"
                    )),
                    sourceCase.context,
                    provider
            );
            require(outcome.target().equals(sourceCase.expected.reference())
                            && sourceCase.expected.health() == Math.min(20, before + 1),
                    "target source should resolve exactly: " + sourceCase.target.source());
        }
    }

    private static void checkRuntimeFailurePropagation() {
        SimulationActor player = SimulationActor.player(ACTOR_ID, "Player");
        RuntimeEntityProvider provider = provider(player);
        AtomicReference<RuntimeActionOutcome> recorded = new AtomicReference<>();
        RuntimeServices services = new RuntimeServices() {
            @Override
            public Optional<RuntimeSubjectReference> initialCurrentEntity(UUID playerId, String sessionId) {
                return Optional.of(player.reference());
            }

            @Override
            public RuntimeEntityProvider entityProvider() {
                return provider;
            }

            @Override
            public void recordActionOutcome(String nodeId, RuntimeActionOutcome outcome) {
                recorded.set(outcome);
            }

            @Override
            public void sendPlayerMessage(UUID playerId, String message) {
            }

            @Override
            public void debug(String message) {
            }

            @Override
            public void scheduleTimer(Duration delay, TimerContinuation continuation) {
            }
        };
        NodeDefinition remove = action("remove-player-runtime", BuiltInBlockCatalog.ACTION_ENTITY_REMOVE, Map.of(
                "target", EntityTargetRef.currentEntity().toJson()
        ));
        BoundedTraceBuffer traces = new BoundedTraceBuffer(8, 100);
        GraphRuntime runtime = new GraphRuntime(
                new GraphCompiler().compile(actionGraph(remove)),
                new InMemoryStateStore(),
                traces,
                services,
                RuntimeLimits.spikeDefaults()
        );
        RuntimeResult result = runtime.start(new TriggerEvent(TRIGGER, "test", ACTOR_ID, "session"));
        require(!result.success()
                        && result.targetError() == null
                        && result.actionError() != null
                        && result.actionError().code() == EntityActionErrorCode.ENTITY_REMOVE_PLAYER_FORBIDDEN
                        && recorded.get() != null
                        && recorded.get().status() == RuntimeActionOutcome.Status.FAILURE
                        && recorded.get().kind().equals(BuiltInBlockCatalog.ACTION_ENTITY_REMOVE)
                        && recorded.get().target().kind() == RuntimeSubjectReference.Kind.PLAYER
                        && !recorded.get().changed()
                        && recorded.get().affectedCount() == 0
                        && traces.get(result.traceId()).orElseThrow().containsMessage(
                        EntityActionErrorCode.ENTITY_REMOVE_PLAYER_FORBIDDEN.id()),
                "GraphRuntime should preserve the structured action error and failure outcome");

        NodeDefinition heal = action("heal-runtime", BuiltInBlockCatalog.ACTION_ENTITY_HEAL, Map.of(
                "target", EntityTargetRef.currentEntity().toJson(), "amount", "1"
        ));
        NodeDefinition kill = action("kill-after-heal", BuiltInBlockCatalog.ACTION_ENTITY_KILL, Map.of(
                "target", EntityTargetRef.currentEntity().toJson()
        ));
        GraphDefinition successGraph = new GraphDefinition(
                "health-success-continuation",
                List.of(trigger(), heal, kill),
                List.of(
                        edge("success-e1", "start", "started", heal.id(), "input"),
                        edge("success-e2", heal.id(), "done", kill.id(), "input")
                ),
                Map.of(TRIGGER, "start")
        );
        RuntimeResult success = new GraphRuntime(
                new GraphCompiler().compile(successGraph),
                new InMemoryStateStore(),
                new BoundedTraceBuffer(8, 100),
                services,
                RuntimeLimits.spikeDefaults()
        ).start(new TriggerEvent(TRIGGER, "test", ACTOR_ID, "session"));
        require(success.success()
                        && player.killed()
                        && recorded.get() != null
                        && recorded.get().kind().equals(BuiltInBlockCatalog.ACTION_ENTITY_KILL),
                "successful health actions should continue through their ordinary done output");
    }

    private static void checkContinuationIdentity() {
        SimulationActor original = new SimulationActor(
                ACTOR_ID, "Original", true, false, Set.of(),
                com.pixelmc.pixellogic.core.simulation.context.SimulationPosition.overworldSpawn(),
                20, 20, false
        );
        SimulationActor replacement = SimulationActor.player(OTHER_ID, "Replacement");
        AtomicReference<RuntimeSubjectReference> initial = new AtomicReference<>(original.reference());
        AtomicReference<TimerContinuation> continuation = new AtomicReference<>();
        RuntimeEntityProvider provider = provider(original, replacement);
        RuntimeServices services = new RuntimeServices() {
            @Override
            public Optional<RuntimeSubjectReference> initialCurrentEntity(UUID playerId, String sessionId) {
                return Optional.ofNullable(initial.get());
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
                continuation.set(scheduled);
            }
        };
        NodeDefinition wait = action("wait", BuiltInBlockCatalog.TIMER_WAIT, Map.of("durationSeconds", "1"));
        NodeDefinition damage = action("damage-after-delay", BuiltInBlockCatalog.ACTION_ENTITY_DAMAGE, Map.of(
                "target", EntityTargetRef.currentEntity().toJson(), "amount", "3", "damageKind", "GENERIC"
        ));
        GraphDefinition graph = new GraphDefinition(
                "health-continuation",
                List.of(trigger(), wait, damage),
                List.of(
                        edge("e1", "start", "started", "wait", "input"),
                        edge("e2", "wait", "timer_completed", "damage-after-delay", "input")
                ),
                Map.of(TRIGGER, "start")
        );
        GraphRuntime runtime = new GraphRuntime(
                new GraphCompiler().compile(graph),
                new InMemoryStateStore(),
                new BoundedTraceBuffer(8, 100),
                services,
                RuntimeLimits.spikeDefaults()
        );
        RuntimeResult waiting = runtime.start(new TriggerEvent(TRIGGER, "test", ACTOR_ID, "session"));
        require(waiting.suspended() && continuation.get() != null, "delay should suspend before the health action");
        initial.set(replacement.reference());
        RuntimeResult resumed = runtime.resumeTimer(continuation.get());
        require(resumed.success() && original.health() == 17 && replacement.health() == 20,
                "continuation should retain the original stable target identity");
    }

    private static RuntimeActionOutcome execute(
            NodeDefinition node,
            RuntimeExecutionContext context,
            RuntimeEntityProvider provider
    ) {
        return services(provider).executeSimulationNode(node, context).orElseThrow().actionOutcome();
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
            require(exception.error().code() == code && exception.error().fieldPath().startsWith(node.id()),
                    "unexpected action error: " + exception.error());
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

    private static RuntimeServices services(RuntimeEntityProvider provider) {
        return new RuntimeServices() {
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
            public void scheduleTimer(Duration delay, TimerContinuation continuation) {
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
                return entity != null
                        && entity.reference().kind() == RuntimeSubjectReference.Kind.PLAYER
                        && entity.online()
                        && !entity.removed()
                        ? RuntimeEntityLookup.resolved(entity)
                        : RuntimeEntityLookup.unresolvable();
            }

            @Override
            public RuntimeOnlinePlayerList listOnlinePlayers(String query, int limit) {
                return new RuntimeOnlinePlayerList(true, List.of());
            }
        };
    }

    private static RuntimeExecutionContext context(
            RuntimeSubjectReference current,
            RuntimeSubjectReference target,
            RuntimeConditionResult condition
    ) {
        return new RuntimeExecutionContext(ACTOR_ID, "session", target, current, condition);
    }

    private static SimulationEntity entity(UUID id, double health, double maxHealth, boolean invulnerable) {
        return new SimulationEntity(id, "minecraft:zombie", "Zombie", Set.of(), true, health, maxHealth, invulnerable);
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

    private record SourceCase(
            EntityTargetRef target,
            RuntimeExecutionContext context,
            RuntimeEntityAccess expected
    ) {
    }

    private static final class DamageSpy extends SimulationEntity {
        private int damageCalls;
        private int setHealthCalls;

        private DamageSpy(UUID id, double health, boolean invulnerable) {
            super(id, "minecraft:zombie", "Damage Spy", Set.of(), true, health, 20, invulnerable);
        }

        @Override
        public synchronized boolean damage(EntityDamageKind kind, double amount) {
            damageCalls += 1;
            return super.damage(kind, amount);
        }

        @Override
        public boolean damageIsApproximate() {
            return false;
        }

        @Override
        public synchronized boolean setHealth(double health) {
            setHealthCalls += 1;
            return super.setHealth(health);
        }
    }
}
