import type { ApiTrace } from '../../model/graphTypes';
import { escapeHtml, formatTime } from '../../utils/dom';
import { humanizeTraceMessage } from '../humanize/labels';

export function renderTrace(trace: ApiTrace | null): string {
  if (!trace) {
    return '<li class="trace-empty">暂无执行记录。启动 API 后点击“测试运行”。</li>';
  }

  return trace.steps
    .map((step) => `<li>[${escapeHtml(formatTime(step.timestamp))}] ${escapeHtml(humanizeTraceMessage(step.message))}</li>`)
    .join('');
}