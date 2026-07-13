export const simulationTagLimit = 32;
export const simulationTagLengthLimit = 64;
export const simulationNameLengthLimit = 64;
export const simulationRegionLimit = 8;
export const simulationRegionNameLengthLimit = 64;
export const simulationCoordinateLimit = 30_000_000;
export const simulationMinY = -2048;
export const simulationMaxY = 4096;
export const simulationMaxHealth = 1_000_000;

const defaultDimensionId = 'minecraft:overworld';
const defaultBlockId = 'minecraft:stone';
const namespacedIdPattern = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;

export type SimulationPosition = {
  dimensionId: string;
  x: number;
  y: number;
  z: number;
};

export type SimulationTargetBlock = SimulationPosition & {
  enabled: boolean;
  blockId: string;
};

export type SimulationRegionFact = {
  name: string;
  dimensionId: string;
  minX: number;
  minY: number;
  minZ: number;
  maxX: number;
  maxY: number;
  maxZ: number;
};

export type SimulationTargetEntity = {
  enabled: boolean;
  entityTypeId: string;
  displayName: string;
  tags: string[];
  living: boolean;
  health: number;
  maxHealth: number;
  invulnerable: boolean;
};

export type SimulationTestWorld = {
  playerPosition: SimulationPosition;
  targetBlock: SimulationTargetBlock;
  targetEntity: SimulationTargetEntity;
  regions: SimulationRegionFact[];
};

export type SimulationTestActor = {
  id: string;
  displayName: string;
  tags: string[];
  operator: boolean;
  health: number;
  maxHealth: number;
  invulnerable: boolean;
};

export type SimulationTestContext = {
  actor: SimulationTestActor;
  world: SimulationTestWorld;
};

export type SimulationTestResult = {
  success: boolean;
  traceId: string;
  message: string;
  actorDisplayName: string;
  actorOperator: boolean;
  playerPosition?: SimulationPosition;
  targetBlock?: SimulationTargetBlock;
  regions?: SimulationRegionFact[];
  initialActorTags: string[];
  initialActorHealth: number;
  actorTags: string[];
  actorHealth: number;
  actorMaxHealth: number;
  actorAlive: boolean;
  actorRemoved: boolean;
  targetEntityEnabled: boolean;
  targetEntityTypeId: string;
  targetEntityDisplayName: string;
  initialTargetEntityTags: string[];
  initialTargetEntityHealth: number;
  targetEntityTags: string[];
  targetEntityHealth: number;
  targetEntityMaxHealth: number;
  targetEntityAlive: boolean;
  targetEntityRemoved: boolean;
  timerScheduled: boolean;
  status: 'WAITING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
};

export function defaultSimulationTestContext(): SimulationTestContext {
  return {
    actor: {
      id: 'webui-sim-player',
      displayName: 'WebUI 模拟玩家',
      tags: [],
      operator: false,
      health: 20,
      maxHealth: 20,
      invulnerable: false,
    },
    world: defaultSimulationWorld(),
  };
}

export function cloneSimulationTestContext(context: SimulationTestContext): SimulationTestContext {
  return structuredClone(withWorldDefaults(context));
}

export function updateSimulationDisplayName(context: SimulationTestContext, displayName: string): SimulationTestContext {
  return {
    ...withWorldDefaults(context),
    actor: {
      ...context.actor,
      displayName,
    },
  };
}

export function updateSimulationOperator(context: SimulationTestContext, operator: boolean): SimulationTestContext {
  return {
    ...withWorldDefaults(context),
    actor: {
      ...context.actor,
      operator,
    },
  };
}

export function updateSimulationActorHealth(
  context: SimulationTestContext,
  field: 'health' | 'maxHealth',
  value: string,
): SimulationTestContext {
  const normalized = withWorldDefaults(context);
  return { ...normalized, actor: { ...normalized.actor, [field]: toNumberInput(value) } };
}

export function updateSimulationActorInvulnerable(
  context: SimulationTestContext,
  invulnerable: boolean,
): SimulationTestContext {
  const normalized = withWorldDefaults(context);
  return { ...normalized, actor: { ...normalized.actor, invulnerable } };
}

export function updateSimulationPlayerPosition(
  context: SimulationTestContext,
  field: keyof SimulationPosition,
  value: string,
): SimulationTestContext {
  const normalized = withWorldDefaults(context);
  return {
    ...normalized,
    world: {
      ...normalized.world,
      playerPosition: updatePosition(normalized.world.playerPosition, field, value),
    },
  };
}

export function updateSimulationTargetEnabled(context: SimulationTestContext, enabled: boolean): SimulationTestContext {
  const normalized = withWorldDefaults(context);
  return {
    ...normalized,
    world: {
      ...normalized.world,
      targetBlock: {
        ...normalized.world.targetBlock,
        enabled,
      },
    },
  };
}

export function updateSimulationTargetBlock(
  context: SimulationTestContext,
  field: keyof SimulationTargetBlock,
  value: string,
): SimulationTestContext {
  const normalized = withWorldDefaults(context);
  const targetBlock = normalized.world.targetBlock;
  return {
    ...normalized,
    world: {
      ...normalized.world,
      targetBlock: {
        ...targetBlock,
        [field]: field === 'dimensionId' || field === 'blockId' ? value : toNumberInput(value),
      },
    },
  };
}

export function updateSimulationTargetEntityEnabled(
  context: SimulationTestContext,
  enabled: boolean,
): SimulationTestContext {
  const normalized = withWorldDefaults(context);
  return {
    ...normalized,
    world: {
      ...normalized.world,
      targetEntity: { ...normalized.world.targetEntity, enabled },
    },
  };
}

export function updateSimulationTargetEntity(
  context: SimulationTestContext,
  field: 'entityTypeId' | 'displayName',
  value: string,
): SimulationTestContext {
  const normalized = withWorldDefaults(context);
  return {
    ...normalized,
    world: {
      ...normalized.world,
      targetEntity: { ...normalized.world.targetEntity, [field]: value },
    },
  };
}

export function updateSimulationTargetEntityHealth(
  context: SimulationTestContext,
  field: 'health' | 'maxHealth',
  value: string,
): SimulationTestContext {
  const normalized = withWorldDefaults(context);
  return {
    ...normalized,
    world: {
      ...normalized.world,
      targetEntity: { ...normalized.world.targetEntity, [field]: toNumberInput(value) },
    },
  };
}

export function updateSimulationTargetEntityFlag(
  context: SimulationTestContext,
  field: 'living' | 'invulnerable',
  value: boolean,
): SimulationTestContext {
  const normalized = withWorldDefaults(context);
  return {
    ...normalized,
    world: {
      ...normalized.world,
      targetEntity: { ...normalized.world.targetEntity, [field]: value },
    },
  };
}

export function addSimulationTargetEntityTag(
  context: SimulationTestContext,
  rawTag: string,
): { context: SimulationTestContext; error: string } {
  const normalized = withWorldDefaults(context);
  const tag = rawTag.trim();
  if (!tag) {
    return { context, error: '标签不能为空。' };
  }
  const tags = normalizeSimulationTags(normalized.world.targetEntity.tags);
  const error = validateTag(tag, tags);
  if (error) {
    return { context, error };
  }
  return {
    context: {
      ...normalized,
      world: {
        ...normalized.world,
        targetEntity: {
          ...normalized.world.targetEntity,
          tags: tags.includes(tag) ? tags : [...tags, tag],
        },
      },
    },
    error: '',
  };
}

export function removeSimulationTargetEntityTag(
  context: SimulationTestContext,
  tag: string,
): SimulationTestContext {
  const normalized = withWorldDefaults(context);
  return {
    ...normalized,
    world: {
      ...normalized.world,
      targetEntity: {
        ...normalized.world.targetEntity,
        tags: normalized.world.targetEntity.tags.filter((item) => item !== tag),
      },
    },
  };
}

export function addSimulationRegion(context: SimulationTestContext): { context: SimulationTestContext; error: string } {
  const normalized = withWorldDefaults(context);
  if (normalized.world.regions.length >= simulationRegionLimit) {
    return { context, error: '测试区域数量不能超过 8 个。' };
  }
  const nextIndex = normalized.world.regions.length + 1;
  return {
    context: {
      ...normalized,
      world: {
        ...normalized.world,
        regions: [
          ...normalized.world.regions,
          {
            name: `测试区域 ${nextIndex}`,
            dimensionId: defaultDimensionId,
            minX: 0,
            minY: 64,
            minZ: 0,
            maxX: 0,
            maxY: 70,
            maxZ: 0,
          },
        ],
      },
    },
    error: '',
  };
}

export function removeSimulationRegion(context: SimulationTestContext, index: number): SimulationTestContext {
  const normalized = withWorldDefaults(context);
  return {
    ...normalized,
    world: {
      ...normalized.world,
      regions: normalized.world.regions.filter((_, itemIndex) => itemIndex !== index),
    },
  };
}

export function updateSimulationRegion(
  context: SimulationTestContext,
  index: number,
  field: keyof SimulationRegionFact,
  value: string,
): SimulationTestContext {
  const normalized = withWorldDefaults(context);
  return {
    ...normalized,
    world: {
      ...normalized.world,
      regions: normalized.world.regions.map((region, itemIndex) => {
        if (itemIndex !== index) {
          return region;
        }
        return {
          ...region,
          [field]: field === 'name' || field === 'dimensionId' ? value : toNumberInput(value),
        };
      }),
    },
  };
}

export function addSimulationTag(context: SimulationTestContext, rawTag: string): { context: SimulationTestContext; error: string } {
  const tag = rawTag.trim();
  const currentTags = normalizeSimulationTags(context.actor.tags);
  if (!tag) {
    return { context, error: '标签不能为空。' };
  }
  const error = validateTag(tag, currentTags);
  if (error) {
    return { context, error };
  }
  if (currentTags.includes(tag)) {
    return { context: { ...withWorldDefaults(context), actor: { ...context.actor, tags: currentTags } }, error: '' };
  }
  return {
    context: {
      ...withWorldDefaults(context),
      actor: {
        ...context.actor,
        tags: [...currentTags, tag],
      },
    },
    error: '',
  };
}

export function removeSimulationTag(context: SimulationTestContext, tag: string): SimulationTestContext {
  return {
    ...withWorldDefaults(context),
    actor: {
      ...context.actor,
      tags: normalizeSimulationTags(context.actor.tags).filter((item) => item !== tag),
    },
  };
}

export function normalizeSimulationTags(tags: string[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  tags.forEach((rawTag) => {
    const tag = rawTag.trim();
    if (tag && !seen.has(tag)) {
      seen.add(tag);
      result.push(tag);
    }
  });
  return result;
}

export function validateSimulationTestContext(context: SimulationTestContext): string {
  const normalized = withWorldDefaults(context);
  const displayName = normalized.actor.displayName.trim();
  if (!displayName) {
    return '测试玩家名称不能为空。';
  }
  if (displayName.length > simulationNameLengthLimit) {
    return '测试玩家名称不能超过 64 个字符。';
  }
  if (hasControlCharacter(displayName)) {
    return '测试玩家名称不能包含换行或控制字符。';
  }

  const tags = normalizeSimulationTags(normalized.actor.tags);
  if (tags.length > simulationTagLimit) {
    return '标签数量不能超过 32 个。';
  }
  for (const tag of tags) {
    const error = validateTag(tag, tags.filter((item) => item !== tag));
    if (error) {
      return error;
    }
  }

  const actorHealthError = validateHealth(normalized.actor.health, normalized.actor.maxHealth, '测试玩家');
  if (actorHealthError) {
    return actorHealthError;
  }

  return validateWorld(normalized.world);
}

export function simulationTestPayload(context: SimulationTestContext): { testContext: SimulationTestContext } {
  const normalized = normalizeSimulationTestContext(context);
  return {
    testContext: normalized,
  };
}

export function formatSimulationPosition(position?: SimulationPosition): string {
  const next = position ?? defaultSimulationPosition();
  return `${next.dimensionId} (${integerOrDefault(next.x, 0)}, ${integerOrDefault(next.y, 64)}, ${integerOrDefault(next.z, 0)})`;
}

export function formatSimulationTargetBlock(targetBlock?: SimulationTargetBlock): string {
  const target = targetBlock ?? defaultSimulationTargetBlock();
  if (!target.enabled) {
    return '未设置';
  }
  return `${target.blockId} @ ${formatSimulationPosition(target)}`;
}

export function formatSimulationRegions(regions?: SimulationRegionFact[]): string {
  const items = regions ?? [];
  if (items.length === 0) {
    return '未设置';
  }
  const names = items.map((region) => region.name).filter(Boolean).slice(0, 3).join('，');
  return `${items.length} 个${names ? `：${names}` : ''}`;
}

function normalizeSimulationTestContext(context: SimulationTestContext): SimulationTestContext {
  const normalized = cloneSimulationTestContext(context);
  return {
    actor: {
      ...normalized.actor,
      displayName: normalized.actor.displayName.trim(),
      tags: normalizeSimulationTags(normalized.actor.tags),
    },
    world: {
      playerPosition: normalizePosition(normalized.world.playerPosition, defaultSimulationPosition()),
      targetBlock: {
        ...normalizePosition(normalized.world.targetBlock, defaultSimulationTargetBlock()),
        enabled: normalized.world.targetBlock.enabled,
        blockId: normalized.world.targetBlock.blockId.trim() || defaultBlockId,
      },
      targetEntity: {
        ...normalized.world.targetEntity,
        entityTypeId: normalized.world.targetEntity.entityTypeId.trim() || 'minecraft:zombie',
        displayName: normalized.world.targetEntity.displayName.trim() || '测试僵尸',
        tags: normalizeSimulationTags(normalized.world.targetEntity.tags),
      },
      regions: normalized.world.regions.map(normalizeRegion),
    },
  };
}

function withWorldDefaults(context: SimulationTestContext): SimulationTestContext {
  const defaultActor = defaultSimulationTestContext().actor;
  const defaultTargetEntity = defaultSimulationTargetEntity();
  return {
    actor: { ...defaultActor, ...context.actor },
    world: {
      playerPosition: context.world?.playerPosition ?? defaultSimulationPosition(),
      targetBlock: context.world?.targetBlock ?? defaultSimulationTargetBlock(),
      targetEntity: { ...defaultTargetEntity, ...context.world?.targetEntity },
      regions: context.world?.regions ?? [],
    },
  };
}

function defaultSimulationWorld(): SimulationTestWorld {
  return {
    playerPosition: defaultSimulationPosition(),
    targetBlock: defaultSimulationTargetBlock(),
    targetEntity: defaultSimulationTargetEntity(),
    regions: [],
  };
}

function defaultSimulationPosition(): SimulationPosition {
  return {
    dimensionId: defaultDimensionId,
    x: 0,
    y: 64,
    z: 0,
  };
}

function defaultSimulationTargetBlock(): SimulationTargetBlock {
  return {
    enabled: false,
    dimensionId: defaultDimensionId,
    x: 0,
    y: 64,
    z: 0,
    blockId: defaultBlockId,
  };
}

export function formatSimulationTargetEntity(targetEntity?: SimulationTargetEntity): string {
  const target = targetEntity ?? defaultSimulationTargetEntity();
  return target.enabled
    ? `${target.displayName || '测试实体'}（${target.entityTypeId || 'minecraft:zombie'}）`
    : '未启用';
}

function defaultSimulationTargetEntity(): SimulationTargetEntity {
  return {
    enabled: false,
    entityTypeId: 'minecraft:zombie',
    displayName: '测试僵尸',
    tags: [],
    living: true,
    health: 20,
    maxHealth: 20,
    invulnerable: false,
  };
}

function updatePosition<T extends SimulationPosition>(position: T, field: keyof SimulationPosition, value: string): T {
  return {
    ...position,
    [field]: field === 'dimensionId' ? value : toNumberInput(value),
  };
}

function normalizePosition<T extends SimulationPosition>(position: T, fallback: T): T {
  return {
    ...position,
    dimensionId: position.dimensionId.trim() || fallback.dimensionId,
    x: integerOrDefault(position.x, fallback.x),
    y: integerOrDefault(position.y, fallback.y),
    z: integerOrDefault(position.z, fallback.z),
  };
}

function normalizeRegion(region: SimulationRegionFact): SimulationRegionFact {
  return {
    name: region.name.trim(),
    dimensionId: region.dimensionId.trim() || defaultDimensionId,
    minX: Math.min(integerOrDefault(region.minX, 0), integerOrDefault(region.maxX, 0)),
    minY: Math.min(integerOrDefault(region.minY, 64), integerOrDefault(region.maxY, 64)),
    minZ: Math.min(integerOrDefault(region.minZ, 0), integerOrDefault(region.maxZ, 0)),
    maxX: Math.max(integerOrDefault(region.minX, 0), integerOrDefault(region.maxX, 0)),
    maxY: Math.max(integerOrDefault(region.minY, 64), integerOrDefault(region.maxY, 64)),
    maxZ: Math.max(integerOrDefault(region.minZ, 0), integerOrDefault(region.maxZ, 0)),
  };
}

function validateWorld(world: SimulationTestWorld): string {
  const positionError = validatePosition(world.playerPosition, '玩家位置');
  if (positionError) {
    return positionError;
  }
  const targetPositionError = validatePosition(world.targetBlock, '目标方块位置');
  if (targetPositionError) {
    return targetPositionError;
  }
  if (!isNamespacedId(world.targetBlock.blockId.trim())) {
    return '目标方块 ID 必须类似 minecraft:stone。';
  }
  if (!isNamespacedId(world.targetEntity.entityTypeId.trim())) {
    return '测试目标实体类型 ID 必须类似 minecraft:zombie。';
  }
  if (!world.targetEntity.displayName.trim()) {
    return '测试目标实体名称不能为空。';
  }
  if (world.targetEntity.displayName.trim().length > simulationNameLengthLimit) {
    return '测试目标实体名称不能超过 64 个字符。';
  }
  if (hasControlCharacter(world.targetEntity.displayName)) {
    return '测试目标实体名称不能包含换行或控制字符。';
  }
  const targetHealthError = validateHealth(world.targetEntity.health, world.targetEntity.maxHealth, '测试目标实体');
  if (targetHealthError) {
    return targetHealthError;
  }
  const targetTags = normalizeSimulationTags(world.targetEntity.tags);
  if (targetTags.length > simulationTagLimit) {
    return '测试目标实体标签数量不能超过 32 个。';
  }
  for (const tag of targetTags) {
    const error = validateTag(tag, targetTags);
    if (error) {
      return error;
    }
  }
  if (world.regions.length > simulationRegionLimit) {
    return '测试区域数量不能超过 8 个。';
  }
  for (const region of world.regions) {
    const error = validateRegion(region);
    if (error) {
      return error;
    }
  }
  return '';
}

function validatePosition(position: SimulationPosition, label: string): string {
  if (!isNamespacedId(position.dimensionId.trim())) {
    return `${label}的维度 ID 不合法。`;
  }
  return validateCoordinateTriplet(position, label);
}

function validateRegion(region: SimulationRegionFact): string {
  const name = region.name.trim();
  if (!name) {
    return '测试区域名称不能为空。';
  }
  if (name.length > simulationRegionNameLengthLimit) {
    return '测试区域名称不能超过 64 个字符。';
  }
  if (hasControlCharacter(name)) {
    return '测试区域名称不能包含换行或控制字符。';
  }
  if (!isNamespacedId(region.dimensionId.trim())) {
    return '测试区域的维度 ID 不合法。';
  }
  return validateCoordinateTriplet({
    x: region.minX,
    y: region.minY,
    z: region.minZ,
  }, '测试区域最小坐标')
    || validateCoordinateTriplet({
      x: region.maxX,
      y: region.maxY,
      z: region.maxZ,
    }, '测试区域最大坐标');
}

function validateCoordinateTriplet(position: Pick<SimulationPosition, 'x' | 'y' | 'z'>, label: string): string {
  const xError = validateCoordinate(position.x, `${label} X`, -simulationCoordinateLimit, simulationCoordinateLimit);
  if (xError) {
    return xError;
  }
  const yError = validateCoordinate(position.y, `${label} Y`, simulationMinY, simulationMaxY);
  if (yError) {
    return yError;
  }
  return validateCoordinate(position.z, `${label} Z`, -simulationCoordinateLimit, simulationCoordinateLimit);
}

function validateCoordinate(value: number, label: string, min: number, max: number): string {
  if (!Number.isInteger(value)) {
    return `${label} 必须是整数。`;
  }
  if (value < min || value > max) {
    return `${label} 超出允许范围。`;
  }
  return '';
}

function validateHealth(health: number, maximum: number, label: string): string {
  if (!Number.isFinite(maximum) || maximum <= 0 || maximum > simulationMaxHealth) {
    return `${label}最大生命值超出允许范围。`;
  }
  if (!Number.isFinite(health) || health < 0 || health > maximum) {
    return `${label}生命值必须在 0 到最大生命值之间。`;
  }
  return '';
}

function validateTag(tag: string, currentTags: string[]): string {
  if (tag.length > simulationTagLengthLimit) {
    return '单个标签不能超过 64 个字符。';
  }
  if (hasControlCharacter(tag)) {
    return '标签不能包含换行或控制字符。';
  }
  if (/\s/.test(tag)) {
    return '标签不能包含空白字符。';
  }
  if (!currentTags.includes(tag) && currentTags.length >= simulationTagLimit) {
    return '标签数量不能超过 32 个。';
  }
  return '';
}

function isNamespacedId(value: string): boolean {
  return namespacedIdPattern.test(value);
}

function toNumberInput(value: string): number {
  if (value.trim() === '') {
    return Number.NaN;
  }
  return Number(value);
}

function integerOrDefault(value: number, fallback: number): number {
  return Number.isInteger(value) ? value : fallback;
}

function hasControlCharacter(value: string): boolean {
  return /[\u0000-\u001f\u007f]/.test(value);
}
