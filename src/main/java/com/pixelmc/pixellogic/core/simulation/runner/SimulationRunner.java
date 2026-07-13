package com.pixelmc.pixellogic.core.simulation.runner;

import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.runtime.GraphRuntime;
import com.pixelmc.pixellogic.core.runtime.RuntimeResult;
import com.pixelmc.pixellogic.core.runtime.RuntimeActionOutcome;
import com.pixelmc.pixellogic.core.runtime.RuntimeEntityProvider;
import com.pixelmc.pixellogic.core.runtime.RuntimeNodeExecutionResult;
import com.pixelmc.pixellogic.core.runtime.RuntimePredicateResult;
import com.pixelmc.pixellogic.core.runtime.RuntimeExecutionContext;
import com.pixelmc.pixellogic.core.runtime.RuntimeSubjectReference;
import com.pixelmc.pixellogic.core.runtime.RuntimeServices;
import com.pixelmc.pixellogic.core.runtime.TriggerEvent;
import com.pixelmc.pixellogic.core.simulation.context.SimulationContext;
import com.pixelmc.pixellogic.core.simulation.context.SimulationActor;
import com.pixelmc.pixellogic.core.simulation.context.SimulationEntity;
import com.pixelmc.pixellogic.core.simulation.context.SimulationWorld;
import com.pixelmc.pixellogic.core.simulation.executor.SimulationExecutionRegistry;
import com.pixelmc.pixellogic.core.simulation.result.SimulationActionResult;
import com.pixelmc.pixellogic.core.simulation.result.SimulationMessageResult;
import com.pixelmc.pixellogic.core.simulation.result.SimulationStateChangeResult;
import com.pixelmc.pixellogic.core.state.StateKey;
import com.pixelmc.pixellogic.core.timer.TimerContinuation;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class SimulationRunner {
    private final RuntimeFactory runtimeFactory;
    private final RuntimeServices delegateServices;
    private final SimulationExecutionRegistry registry;

    public SimulationRunner(
            RuntimeFactory runtimeFactory,
            RuntimeServices delegateServices,
            SimulationExecutionRegistry registry
    ) {
        this.runtimeFactory = runtimeFactory;
        this.delegateServices = delegateServices;
        this.registry = registry;
    }

    public SimulationExecutionResult run(SimulationExecutionRequest request) {
        return run(request, ignored -> {
        });
    }

    public SimulationExecutionResult run(
            SimulationExecutionRequest request,
            Consumer<SimulationExecutionResult> resultObserver
    ) {
        SimulationActor actor = copyActor(request.actor());
        SimulationWorld world = copyWorld(request.world());
        Set<String> initialActorTags = actor.tags();
        double initialActorHealth = actor.health();
        Set<String> initialTargetEntityTags = world.targetEntity() == null
                ? Set.of()
                : world.targetEntity().tags();
        double initialTargetEntityHealth = world.targetEntity() == null ? 0 : world.targetEntity().health();
        SimulationContext context = new SimulationContext(
                UUID.randomUUID().toString(),
                request.generation(),
                actor,
                world,
                delegateServices.entityProvider()
        );
        GraphRuntime runtime = runtimeFactory.create(new SimulationRuntimeServices(
                delegateServices,
                registry,
                context,
                initialActorTags,
                initialActorHealth,
                initialTargetEntityTags,
                initialTargetEntityHealth,
                request.initializeCurrentEntity(),
                resultObserver
        ));
        RuntimeResult result = runtime.start(new TriggerEvent(
                request.triggerType(),
                request.commandText(),
                request.actor().id(),
                request.sessionId()
        ));
        return SimulationExecutionResult.from(
                result,
                context,
                initialActorTags,
                initialActorHealth,
                initialTargetEntityTags,
                initialTargetEntityHealth
        );
    }

    private static SimulationActor copyActor(SimulationActor actor) {
        return new SimulationActor(
                actor.id(),
                actor.displayName(),
                actor.online(),
                actor.operator(),
                actor.tags(),
                actor.position(),
                actor.health(),
                actor.maxHealth(),
                actor.invulnerable()
        );
    }

    private static SimulationWorld copyWorld(SimulationWorld world) {
        SimulationEntity target = world.targetEntity();
        SimulationEntity targetCopy = target == null
                ? null
                : new SimulationEntity(
                        target.id(),
                        target.entityTypeId(),
                        target.displayName(),
                        target.tags(),
                        target.living(),
                        target.health(),
                        target.maxHealth(),
                        target.invulnerable()
                );
        return new SimulationWorld(world.defaultDimensionId(), world.targetBlock(), world.regions(), targetCopy);
    }

    public interface RuntimeFactory {
        GraphRuntime create(RuntimeServices services);
    }

    private static final class SimulationRuntimeServices implements RuntimeServices {
        private final RuntimeServices delegate;
        private final SimulationExecutionRegistry registry;
        private final SimulationContext context;
        private final Set<String> initialActorTags;
        private final double initialActorHealth;
        private final Set<String> initialTargetEntityTags;
        private final double initialTargetEntityHealth;
        private final boolean initializeCurrentEntity;
        private final Consumer<SimulationExecutionResult> resultObserver;

        private SimulationRuntimeServices(
                RuntimeServices delegate,
                SimulationExecutionRegistry registry,
                SimulationContext context,
                Set<String> initialActorTags,
                double initialActorHealth,
                Set<String> initialTargetEntityTags,
                double initialTargetEntityHealth,
                boolean initializeCurrentEntity,
                Consumer<SimulationExecutionResult> resultObserver
        ) {
            this.delegate = delegate;
            this.registry = registry;
            this.context = context;
            this.initialActorTags = initialActorTags;
            this.initialActorHealth = initialActorHealth;
            this.initialTargetEntityTags = initialTargetEntityTags;
            this.initialTargetEntityHealth = initialTargetEntityHealth;
            this.initializeCurrentEntity = initializeCurrentEntity;
            this.resultObserver = resultObserver;
        }

        @Override
        public Optional<RuntimeNodeExecutionResult> executeSimulationNode(
                NodeDefinition node,
                RuntimeExecutionContext runtimeContext
        ) {
            return registry.execute(node, context, runtimeContext, delegate)
                    .or(() -> RuntimeServices.super.executeSimulationNode(node, runtimeContext));
        }

        @Override
        public Optional<RuntimePredicateResult> evaluatePredicate(
                NodeDefinition node,
                RuntimeExecutionContext runtimeContext
        ) {
            return registry.evaluatePredicate(node, context, runtimeContext, delegate)
                    .or(() -> RuntimeServices.super.evaluatePredicate(node, runtimeContext));
        }

        @Override
        public Optional<RuntimeSubjectReference> initialCurrentEntity(UUID playerId, String sessionId) {
            return initializeCurrentEntity ? Optional.of(context.actor().reference()) : Optional.empty();
        }

        @Override
        public Optional<RuntimeSubjectReference> targetEntity(UUID playerId, String sessionId) {
            return context.targetEntity().map(SimulationEntity::reference);
        }

        @Override
        public RuntimeEntityProvider entityProvider() {
            return context;
        }

        @Override
        public void sendPlayerMessage(UUID playerId, String message) {
            delegate.sendPlayerMessage(playerId, message);
        }

        @Override
        public void debug(String message) {
            delegate.debug(message);
        }

        @Override
        public void scheduleTimer(Duration delay, TimerContinuation continuation) {
            delegate.scheduleTimer(delay, continuation);
        }

        @Override
        public void recordActionResult(String nodeId, String kind, String message) {
            context.addActionResult(new SimulationActionResult(nodeId, kind, message));
        }

        @Override
        public void recordActionOutcome(String nodeId, RuntimeActionOutcome outcome) {
            context.addActionResult(new SimulationActionResult(nodeId, outcome.kind(), outcome.message(), outcome));
        }

        @Override
        public void recordEntityTagState(
                String nodeId,
                RuntimeSubjectReference target,
                Set<String> tags
        ) {
            String key = context.actor().reference().equals(target)
                    ? "actor.tags"
                    : "entity." + target.id() + ".tags";
            context.addStateChange(new SimulationStateChangeResult(nodeId, key, String.join(",", tags)));
        }

        @Override
        public void recordMessageResult(String nodeId, UUID playerId, String message, String channel) {
            context.addMessageResult(new SimulationMessageResult(nodeId, playerId, message, channel));
        }

        @Override
        public void recordStateChange(String nodeId, StateKey key, String value) {
            context.addStateChange(new SimulationStateChangeResult(nodeId, key.scope() + "." + key.key(), value));
        }

        @Override
        public void recordTimerScheduled(String nodeId, Duration delay, TimerContinuation continuation) {
            context.markTimerScheduled();
        }

        @Override
        public void recordRuntimeResult(RuntimeResult result) {
            resultObserver.accept(SimulationExecutionResult.from(
                    result,
                    context,
                    initialActorTags,
                    initialActorHealth,
                    initialTargetEntityTags,
                    initialTargetEntityHealth
            ));
        }
    }
}
