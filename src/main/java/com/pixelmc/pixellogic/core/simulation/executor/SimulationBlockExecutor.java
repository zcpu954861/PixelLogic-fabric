package com.pixelmc.pixellogic.core.simulation.executor;

import com.pixelmc.pixellogic.core.model.NodeDefinition;
import com.pixelmc.pixellogic.core.model.NodeType;
import com.pixelmc.pixellogic.core.runtime.RuntimeServices;
import com.pixelmc.pixellogic.core.simulation.context.SimulationContext;

public interface SimulationBlockExecutor {
    NodeType nodeType();

    RuntimeServices.NodeExecution execute(NodeDefinition node, SimulationContext context);
}
