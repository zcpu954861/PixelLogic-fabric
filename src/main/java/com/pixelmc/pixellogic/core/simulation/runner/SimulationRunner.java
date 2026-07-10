package com.pixelmc.pixellogic.core.simulation.runner;

import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.runtime.GraphRuntime;
import com.pixelmc.pixellogic.core.runtime.RuntimeResult;
import com.pixelmc.pixellogic.core.runtime.RuntimeNodeExecutionResult;
import com.pixelmc.pixellogic.core.runtime.RuntimeServices;
import com.pixelmc.pixellogic.core.runtime.TriggerEvent;
import com.pixelmc.pixellogic.core.simulation.context.SimulationContext;
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
        Set<String> initialActorTags = request.actor().tags();
        SimulationContext context = new SimulationContext(
                UUID.randomUUID().toString(),
                request.generation(),
                request.actor(),
                request.world(),
                request.event(),
                request.options()
        );
        GraphRuntime runtime = runtimeFactory.create(new SimulationRuntimeServices(
                delegateServices,
                registry,
                context,
                initialActorTags,
                resultObserver
        ));
        RuntimeResult result = runtime.start(new TriggerEvent(
                request.event().triggerType(),
                request.event().commandText(),
                request.actor().id(),
                request.event().sessionId()
        ));
        return SimulationExecutionResult.from(result, context, initialActorTags);
    }

    public interface RuntimeFactory {
        GraphRuntime create(RuntimeServices services);
    }

    private static final class SimulationRuntimeServices implements RuntimeServices {
        private final RuntimeServices delegate;
        private final SimulationExecutionRegistry registry;
        private final SimulationContext context;
        private final Set<String> initialActorTags;
        private final Consumer<SimulationExecutionResult> resultObserver;

        private SimulationRuntimeServices(
                RuntimeServices delegate,
                SimulationExecutionRegistry registry,
                SimulationContext context,
                Set<String> initialActorTags,
                Consumer<SimulationExecutionResult> resultObserver
        ) {
            this.delegate = delegate;
            this.registry = registry;
            this.context = context;
            this.initialActorTags = initialActorTags;
            this.resultObserver = resultObserver;
        }

        @Override
        public Optional<RuntimeNodeExecutionResult> executeSimulationNode(NodeDefinition node, UUID playerId, String sessionId) {
            return registry.execute(node, context, delegate);
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
            if (context.options().realTimeTimers()) {
                delegate.scheduleTimer(delay, continuation);
            }
        }

        @Override
        public void recordActionResult(String nodeId, String kind, String message) {
            context.addActionResult(new SimulationActionResult(nodeId, kind, message));
        }

        @Override
        public void recordMessageResult(String nodeId, UUID playerId, String message) {
            context.addMessageResult(new SimulationMessageResult(nodeId, playerId, message));
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
            resultObserver.accept(SimulationExecutionResult.from(result, context, initialActorTags));
        }
    }
}
