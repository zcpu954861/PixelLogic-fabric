export const simulationTagLimit = 32;
export const simulationTagLengthLimit = 64;
export const simulationNameLengthLimit = 64;

export type SimulationTestActor = {
  id: string;
  displayName: string;
  tags: string[];
  operator: boolean;
};

export type SimulationTestContext = {
  actor: SimulationTestActor;
};

export type SimulationTestResult = {
  success: boolean;
  traceId: string;
  message: string;
  actorDisplayName: string;
  actorOperator: boolean;
  initialActorTags: string[];
  actorTags: string[];
  timerScheduled: boolean;
};

export function defaultSimulationTestContext(): SimulationTestContext {
  return {
    actor: {
      id: 'webui-sim-player',
      displayName: 'WebUI 模拟玩家',
      tags: [],
      operator: false,
    },
  };
}

export function updateSimulationDisplayName(context: SimulationTestContext, displayName: string): SimulationTestContext {
  return {
    actor: {
      ...context.actor,
      displayName,
    },
  };
}

export function updateSimulationOperator(context: SimulationTestContext, operator: boolean): SimulationTestContext {
  return {
    actor: {
      ...context.actor,
      operator,
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
    return { context: { actor: { ...context.actor, tags: currentTags } }, error: '' };
  }
  return {
    context: {
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
  const displayName = context.actor.displayName.trim();
  if (!displayName) {
    return '测试玩家名称不能为空。';
  }
  if (displayName.length > simulationNameLengthLimit) {
    return '测试玩家名称不能超过 64 个字符。';
  }
  if (hasControlCharacter(displayName)) {
    return '测试玩家名称不能包含换行或控制字符。';
  }

  const tags = normalizeSimulationTags(context.actor.tags);
  if (tags.length > simulationTagLimit) {
    return '标签数量不能超过 32 个。';
  }
  for (const tag of tags) {
    const error = validateTag(tag, tags.filter((item) => item !== tag));
    if (error) {
      return error;
    }
  }
  return '';
}

export function simulationTestPayload(context: SimulationTestContext): { testContext: SimulationTestContext } {
  return {
    testContext: {
      actor: {
        ...context.actor,
        displayName: context.actor.displayName.trim(),
        tags: normalizeSimulationTags(context.actor.tags),
      },
    },
  };
}

function validateTag(tag: string, currentTags: string[]): string {
  if (tag.length > simulationTagLengthLimit) {
    return '单个标签不能超过 64 个字符。';
  }
  if (hasControlCharacter(tag)) {
    return '标签不能包含换行或控制字符。';
  }
  if (!currentTags.includes(tag) && currentTags.length >= simulationTagLimit) {
    return '标签数量不能超过 32 个。';
  }
  return '';
}

function hasControlCharacter(value: string): boolean {
  return /[\u0000-\u001f\u007f]/.test(value);
}
