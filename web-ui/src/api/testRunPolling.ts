import { api } from './pixelLogicApi';
import type { ApiResponse } from '../model/graphTypes';

const pollDelayMs = 750;
const failureLimit = 3;
let activeRunId: string | null = null;
let pollTimer: number | null = null;
let pollToken = 0;
let failures = 0;

type PollHandlers = {
  onUpdate: (response: ApiResponse) => void;
  onFailure: (error: unknown) => void;
};

export function startTestRunPolling(runId: string, handlers: PollHandlers): void {
  stopTestRunPolling();
  activeRunId = runId;
  schedulePoll(runId, pollToken, handlers);
}

export function stopTestRunPolling(): void {
  pollToken += 1;
  activeRunId = null;
  failures = 0;
  if (pollTimer !== null) {
    window.clearTimeout(pollTimer);
    pollTimer = null;
  }
}

function schedulePoll(runId: string, token: number, handlers: PollHandlers): void {
  if (activeRunId !== runId || token !== pollToken || pollTimer !== null) {
    return;
  }
  pollTimer = window.setTimeout(() => {
    pollTimer = null;
    void pollRun(runId, token, handlers);
  }, pollDelayMs);
}

async function pollRun(runId: string, token: number, handlers: PollHandlers): Promise<void> {
  try {
    const response = await api(`/api/pixellogic/test/runs/${encodeURIComponent(runId)}`);
    if (activeRunId !== runId || token !== pollToken) {
      return;
    }
    if (response.runId !== runId || typeof response.terminal !== 'boolean') {
      stopTestRunPolling();
      return;
    }
    failures = 0;
    if (response.terminal) {
      stopTestRunPolling();
    } else {
      schedulePoll(runId, token, handlers);
    }
    handlers.onUpdate(response);
  } catch (error) {
    if (activeRunId !== runId || token !== pollToken) {
      return;
    }
    failures += 1;
    if (failures >= failureLimit) {
      stopTestRunPolling();
      handlers.onFailure(error);
      return;
    }
    schedulePoll(runId, token, handlers);
  }
}
