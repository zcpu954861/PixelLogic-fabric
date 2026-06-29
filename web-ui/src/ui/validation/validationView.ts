import type { UiState, ValidationReport } from '../../model/graphTypes';
import { escapeHtml } from '../../utils/dom';

export function validationList(state: UiState, autoSaveInFlight: boolean): string {
  const validation = state.validation;
  if (autoSaveInFlight) {
    return '<li><span class="warn"></span>正在自动保存并检查</li>';
  }
  if (!validation) {
    return '<li><span class="warn"></span>修改后会自动保存并检查</li>';
  }
  if (validation.valid) {
    return '<li><span class="ok"></span>自动检查通过</li>';
  }
  return validation.issues
    .map((issue) => `<li><span class="warn"></span>${escapeHtml(issue.message)}</li>`)
    .join('');
}

export function validationErrorText(validation: ValidationReport | null): string {
  const issues = validation?.issues ?? [];
  if (issues.length === 0) {
    return '保存失败：请修复验证问题。';
  }
  return `保存失败：${issues.map((issue) => issue.message).join('；')}`;
}

export function validationSummaryText(state: UiState): string {
  if (!state.validation) {
    return state.dirty ? '修改后会自动保存并检查。' : '尚未产生新的检查结果。';
  }
  if (state.validation.valid) {
    return '自动检查通过。';
  }
  return validationErrorText(state.validation);
}

export function validationTitle(state: UiState, autoSaveInFlight: boolean): string {
  if (autoSaveInFlight) {
    return '保存中';
  }
  if (state.dirty) {
    return '待保存';
  }
  if (state.validation?.valid) {
    return '通过';
  }
  if (state.validation && !state.validation.valid) {
    return '失败';
  }
  return state.hasDraft ? '未完成' : '已保存';
}

export function uncommittedNotice(state: UiState): string {
  if (state.dirty) {
    return '当前改动正在等待自动保存。';
  }
  if (state.hasDraft) {
    return '当前有未生效草稿，测试运行前会自动检查。';
  }
  return '';
}

export function draftStatusText(state: UiState, autoSaveInFlight: boolean): string {
  if (autoSaveInFlight) {
    return '自动保存中';
  }
  if (state.dirty) {
    return '等待自动保存';
  }
  if (state.hasDraft) {
    return '有待修复草稿';
  }
  return '已自动保存';
}