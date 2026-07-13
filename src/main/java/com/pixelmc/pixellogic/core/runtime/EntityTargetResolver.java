package com.pixelmc.pixellogic.core.runtime;

import com.pixelmc.pixellogic.core.model.EntityTargetRef;
import com.pixelmc.pixellogic.core.model.EntityTargetRequirement;
import com.pixelmc.pixellogic.core.model.EntityTargetSource;

public final class EntityTargetResolver {
    private EntityTargetResolver() {
    }

    public static ResolvedEntityTarget resolve(
            String nodeId,
            String fieldPath,
            String rawTarget,
            EntityTargetRequirement requirement,
            RuntimeExecutionContext context,
            RuntimeEntityProvider provider
    ) {
        EntityTargetRef target;
        try {
            target = EntityTargetRef.parse(rawTarget);
        } catch (IllegalArgumentException exception) {
            throw failure(
                    EntityTargetErrorCode.ENTITY_TARGET_INVALID_CONFIG,
                    nodeId,
                    fieldPath,
                    null,
                    requirement,
                    "",
                    "实体目标配置无效：" + exception.getMessage()
            );
        }
        return resolve(nodeId, fieldPath, target, requirement, context, provider);
    }

    public static ResolvedEntityTarget resolve(
            String nodeId,
            String fieldPath,
            EntityTargetRef target,
            EntityTargetRequirement requirement,
            RuntimeExecutionContext context,
            RuntimeEntityProvider provider
    ) {
        if (target == null || requirement == null || context == null) {
            throw failure(
                    EntityTargetErrorCode.ENTITY_TARGET_INVALID_CONFIG,
                    nodeId,
                    fieldPath,
                    target == null ? null : target.source(),
                    requirement,
                    "",
                    "实体目标配置无效。"
            );
        }
        RuntimeEntityProvider actualProvider = provider == null ? RuntimeEntityProvider.UNAVAILABLE : provider;
        RuntimeSubjectReference requested = switch (target.source()) {
            case CURRENT_ENTITY -> context.currentEntity();
            case CONDITION_SUBJECT -> context.currentCondition() == null ? null : context.currentCondition().subject();
            case TARGET_ENTITY -> context.targetEntity();
            case ONLINE_PLAYER -> null;
        };
        if (target.source() != EntityTargetSource.ONLINE_PLAYER && requested == null) {
            throw failure(
                    EntityTargetErrorCode.ENTITY_TARGET_MISSING,
                    nodeId,
                    fieldPath,
                    target.source(),
                    requirement,
                    "",
                    missingMessage(target.source())
            );
        }
        if (requested != null && !requested.isEntity()) {
            throw failure(
                    EntityTargetErrorCode.ENTITY_TARGET_TYPE_MISMATCH,
                    nodeId,
                    fieldPath,
                    target.source(),
                    requirement,
                    requested.id(),
                    "所选目标不是实体。"
            );
        }

        RuntimeEntityLookup lookup = target.source() == EntityTargetSource.ONLINE_PLAYER
                ? actualProvider.resolveOnlinePlayer(target.playerUuid())
                : actualProvider.resolve(requested);
        String referenceId = target.source() == EntityTargetSource.ONLINE_PLAYER
                ? target.playerUuid().toString()
                : requested.id();
        RuntimeEntityAccess entity = resolvedEntity(
                lookup,
                nodeId,
                fieldPath,
                target.source(),
                requirement,
                referenceId
        );
        RuntimeSubjectReference resolved = entity.reference();
        if (resolved == null || !resolved.isEntity() || resolved.id().isBlank()) {
            throw failure(
                    EntityTargetErrorCode.ENTITY_TARGET_UNRESOLVABLE,
                    nodeId,
                    fieldPath,
                    target.source(),
                    requirement,
                    referenceId,
                    "所选实体无法解析。"
            );
        }
        enforceSource(nodeId, fieldPath, target, requirement, requested, resolved, entity);
        enforceRequirement(nodeId, fieldPath, target.source(), requirement, resolved, entity);
        return new ResolvedEntityTarget(target.source(), resolved, entity);
    }

    private static void enforceSource(
            String nodeId,
            String fieldPath,
            EntityTargetRef target,
            EntityTargetRequirement requirement,
            RuntimeSubjectReference requested,
            RuntimeSubjectReference reference,
            RuntimeEntityAccess entity
    ) {
        if (target.source() != EntityTargetSource.ONLINE_PLAYER) {
            if (!requested.id().equals(reference.id()) || requested.kind() != reference.kind()) {
                throw failure(
                        EntityTargetErrorCode.ENTITY_TARGET_UNRESOLVABLE,
                        nodeId,
                        fieldPath,
                        target.source(),
                        requirement,
                        requested.id(),
                        "所选实体的身份解析结果不一致。"
                );
            }
            return;
        }
        if (reference.kind() != RuntimeSubjectReference.Kind.PLAYER) {
            throw failure(
                    EntityTargetErrorCode.ENTITY_TARGET_TYPE_MISMATCH,
                    nodeId,
                    fieldPath,
                    target.source(),
                    requirement,
                    reference.id(),
                    "指定在线玩家解析结果不是玩家。"
            );
        }
        if (!target.playerUuid().toString().equals(reference.id())) {
            throw failure(
                    EntityTargetErrorCode.ENTITY_TARGET_UNRESOLVABLE,
                    nodeId,
                    fieldPath,
                    target.source(),
                    requirement,
                    target.playerUuid().toString(),
                    "指定在线玩家的身份解析结果不一致。"
            );
        }
        if (!entity.online()) {
            throw failure(
                    EntityTargetErrorCode.ENTITY_TARGET_OFFLINE,
                    nodeId,
                    fieldPath,
                    target.source(),
                    requirement,
                    reference.id(),
                    "所选玩家当前不在线。"
            );
        }
    }

    private static RuntimeEntityAccess resolvedEntity(
            RuntimeEntityLookup lookup,
            String nodeId,
            String fieldPath,
            EntityTargetSource source,
            EntityTargetRequirement requirement,
            String referenceId
    ) {
        RuntimeEntityLookup actual = lookup == null ? RuntimeEntityLookup.providerUnavailable() : lookup;
        return switch (actual.status()) {
            case RESOLVED -> actual.entity();
            case OFFLINE -> throw failure(
                    EntityTargetErrorCode.ENTITY_TARGET_OFFLINE,
                    nodeId,
                    fieldPath,
                    source,
                    requirement,
                    referenceId,
                    "所选玩家当前不在线。"
            );
            case UNRESOLVABLE -> throw failure(
                    EntityTargetErrorCode.ENTITY_TARGET_UNRESOLVABLE,
                    nodeId,
                    fieldPath,
                    source,
                    requirement,
                    referenceId,
                    "所选实体无法解析。"
            );
            case PROVIDER_UNAVAILABLE -> throw failure(
                    EntityTargetErrorCode.ENTITY_TARGET_PROVIDER_UNAVAILABLE,
                    nodeId,
                    fieldPath,
                    source,
                    requirement,
                    referenceId,
                    "实体提供器当前不可用。"
            );
        };
    }

    private static void enforceRequirement(
            String nodeId,
            String fieldPath,
            EntityTargetSource source,
            EntityTargetRequirement requirement,
            RuntimeSubjectReference reference,
            RuntimeEntityAccess entity
    ) {
        if (requirement == EntityTargetRequirement.PLAYER_ONLY
                && reference.kind() != RuntimeSubjectReference.Kind.PLAYER) {
            throw failure(
                    EntityTargetErrorCode.ENTITY_TARGET_TYPE_MISMATCH,
                    nodeId,
                    fieldPath,
                    source,
                    requirement,
                    reference.id(),
                    "所选目标不是玩家。"
            );
        }
        if (requirement == EntityTargetRequirement.PLAYER_ONLY && !entity.online()) {
            throw failure(
                    EntityTargetErrorCode.ENTITY_TARGET_OFFLINE,
                    nodeId,
                    fieldPath,
                    source,
                    requirement,
                    reference.id(),
                    "所选玩家当前不在线。"
            );
        }
        if (requirement == EntityTargetRequirement.LIVING_ENTITY && !entity.living()) {
            throw failure(
                    EntityTargetErrorCode.ENTITY_TARGET_TYPE_MISMATCH,
                    nodeId,
                    fieldPath,
                    source,
                    requirement,
                    reference.id(),
                    "所选目标不是活体实体。"
            );
        }
        if (requirement == EntityTargetRequirement.LIVING_ENTITY && !entity.alive()) {
            throw failure(
                    EntityTargetErrorCode.ENTITY_TARGET_NOT_ALIVE,
                    nodeId,
                    fieldPath,
                    source,
                    requirement,
                    reference.id(),
                    "所选实体已不存活。"
            );
        }
    }

    private static EntityTargetException failure(
            EntityTargetErrorCode code,
            String nodeId,
            String fieldPath,
            EntityTargetSource source,
            EntityTargetRequirement requirement,
            String referenceId,
            String message
    ) {
        String path = fieldPath == null || fieldPath.isBlank()
                ? nodeId
                : (nodeId == null || nodeId.isBlank() ? fieldPath : nodeId + "." + fieldPath);
        return new EntityTargetException(new EntityTargetError(
                code,
                nodeId,
                path,
                source,
                requirement,
                referenceId,
                message
        ));
    }

    private static String missingMessage(EntityTargetSource source) {
        return switch (source) {
            case CURRENT_ENTITY -> "当前执行实体缺失。";
            case CONDITION_SUBJECT -> "当前路径没有可用的条件主体。";
            case TARGET_ENTITY -> "当前目标实体缺失。";
            case ONLINE_PLAYER -> "指定在线玩家缺失。";
        };
    }
}
