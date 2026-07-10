package com.pixelmc.pixellogic.core.simulation.executor;

import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.runtime.RuntimeNodeExecutionResult;
import com.pixelmc.pixellogic.core.runtime.RuntimeExecutionContext;
import com.pixelmc.pixellogic.core.runtime.RuntimeServices;
import com.pixelmc.pixellogic.core.simulation.context.SimulationContext;

public interface SimulationBlockExecutor {
    NodeType nodeType();

    default RuntimeNodeExecutionResult execute(NodeDefinition node, SimulationContext context, RuntimeServices services) {
        throw new IllegalStateException("执行器需要运行时上下文：" + node.type());
    }

    default RuntimeNodeExecutionResult execute(
            NodeDefinition node,
            SimulationContext context,
            RuntimeExecutionContext runtimeContext,
            RuntimeServices services
    ) {
        return execute(node, context, services);
    }
}
