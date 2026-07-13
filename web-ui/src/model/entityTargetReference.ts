import type {
  EntityTargetRef,
  EntityTargetSource,
  GraphConfig,
  GraphConfigValue,
} from './graphTypes';

export const entityTargetSources: ReadonlyArray<{ value: EntityTargetSource; label: string }> = [
  { value: 'CURRENT_ENTITY', label: '当前执行实体' },
  { value: 'CONDITION_SUBJECT', label: '当前条件主体' },
  { value: 'TARGET_ENTITY', label: '当前目标实体' },
  { value: 'ONLINE_PLAYER', label: '指定在线玩家' },
];

export function graphConfigString(config: GraphConfig, key: string, fallback = ''): string {
  const value = config[key];
  return typeof value === 'string' ? value : fallback;
}

export function entityTargetRef(config: GraphConfig, key = 'target'): EntityTargetRef | null {
  return isEntityTargetRef(config[key]) ? config[key] : null;
}

export function entityTargetDraftRef(config: GraphConfig, key = 'target'): EntityTargetRef | null {
  const value = config[key];
  return isEntityTargetRef(value) || isUnselectedOnlinePlayerTarget(value) ? value : null;
}

export function decodeCatalogEntityTarget(value: GraphConfigValue | undefined): EntityTargetRef | null {
  if (isEntityTargetRef(value)) {
    return structuredClone(value);
  }
  if (typeof value !== 'string') {
    return null;
  }
  try {
    const parsed = JSON.parse(value) as unknown;
    return isEntityTargetRef(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

export function isEntityTargetRef(value: unknown): value is EntityTargetRef {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const target = value as Partial<EntityTargetRef>;
  const keys = Object.keys(target);
  if (keys.some((key) => !['source', 'playerUuid', 'playerNameHint'].includes(key))
      || !entityTargetSources.some((source) => source.value === target.source)) {
    return false;
  }
  if (target.source !== 'ONLINE_PLAYER') {
    return target.playerUuid === undefined && target.playerNameHint === undefined;
  }
  return typeof target.playerUuid === 'string'
    && /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(target.playerUuid)
    && (target.playerNameHint === undefined
      || (typeof target.playerNameHint === 'string'
        && target.playerNameHint.length <= 64
        && !/[\u0000-\u001f\u007f-\u009f]/.test(target.playerNameHint)));
}

function isUnselectedOnlinePlayerTarget(value: unknown): value is EntityTargetRef {
  return !!value
    && typeof value === 'object'
    && Object.keys(value).length === 1
    && (value as Partial<EntityTargetRef>).source === 'ONLINE_PLAYER';
}

export function entityTargetLabel(target: EntityTargetRef | null): string {
  if (!target) {
    return '目标未配置';
  }
  if (target.source === 'ONLINE_PLAYER') {
    const hint = target.playerNameHint?.trim();
    return hint ? `玩家 ${hint}` : target.playerUuid ? `玩家 ${shortUuid(target.playerUuid)}` : '指定在线玩家';
  }
  return entityTargetSources.find((source) => source.value === target.source)?.label ?? '目标未配置';
}

export function targetForSource(source: EntityTargetSource): EntityTargetRef {
  return { source };
}

export function targetForOnlinePlayer(uuid: string, name: string): EntityTargetRef {
  return { source: 'ONLINE_PLAYER', playerUuid: uuid, playerNameHint: name };
}

function shortUuid(uuid: string): string {
  return uuid.length > 8 ? `${uuid.slice(0, 8)}…` : uuid;
}
