package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.model.EntityTargetSource;

public record ResolvedEntityTarget(
        EntityTargetSource source,
        RuntimeSubjectReference reference,
        RuntimeEntityAccess entity
) {
}
