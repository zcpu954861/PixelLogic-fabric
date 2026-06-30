import { PixelLogicApiError, api } from '../api/pixelLogicApi';
import { edge, fallbackGraph, graphId, input, node, out } from '../model/demoGraph';
import { state, world } from '../state/appState';
import {
  blockMetrics,
  blockSize,
  branchForNode,
  cloneGraph,
  connectedComponentNodeIds,
  connectedGraphEdges,
  downstreamNodeIds,
  fallbackPosition,
  inputCenterOffset,
  normalizeConditionBranchLayout,
  nodePosition,
  outputCenterOffset,
  preferredMainOutput,
} from '../model/graphLayout';import { escapeAttr, escapeHtml, formatTime, shortFingerprint, shortTraceId } from '../utils/dom';
import type {
  ApiResponse,
  ApiStatus,
  ApiTrace,
  BlockDrag,
  BlockKind,
  BlockMetrics,
  Branch,
  EditableField,
  EditorSection,
  FieldOption,
  GraphDocument,
  GraphEdge,
  GraphHistoryEntry,
  GraphNode,
  GraphPosition,
  GraphSlot,
  InsertCandidate,
  LaneSpan,
  LibraryKind,
  SlotBlock,
  SlotJoin,
  UiState,
} from '../model/graphTypes';
import {
  blockKind,
  booleanLabel,
  booleanOptions,
  humanizeTraceMessage,
  nodeSummary,
  nodeTypeLabel,
  slotLabel,
  stateScopeOptions,
  stateValueLabel,
  valueTypeOptions,
} from './humanize/labels';
import { autoSaveDelayMs, connectedOverlap, conditionBlockWidth, conditionBranchGap, doubleClickMs, dragThreshold, historyLimit, insertSnapX, insertSnapY, normalBlockHeight, normalBlockWidth, puzzleMouthHalfHeight, reconnectSnapX, reconnectSnapY } from './canvas/blockConstants';
import { renderBlock, renderSlotJoin } from './canvas/blockView';
import {
  connectedActionText,
  findInsertCandidate,
  graphWithPreviewPositions,
  insertDraggedGroup,
  makeInsertionGap,
  snapDraggedGroupToCandidate,
} from './canvas/dragInsert';
import { renderEditorModal } from './editor/blockEditorModal';
import { nodeConfigItems, renderNodeEditor } from './editor/formControls';
import { renderTrace } from './trace/traceView';
import { draftStatusText, uncommittedNotice, validationErrorText, validationList, validationSummaryText, validationTitle } from './validation/validationView';
import { renderNodeInfo } from './sidebar/selectionSummary';


const app = document.querySelector<HTMLDivElement>('#app');

let scale = 0.86;
let offsetX = 28;
let offsetY = 34;
let isPanning = false;
let panStart = { x: 0, y: 0 };
let panOffset = { x: 0, y: 0 };
let activeBlockDrag: BlockDrag | null = null;
let lastBlockClick: { nodeId: string; time: number } | null = null;
let graphVersion = 0;
let autoSaveTimer: number | null = null;
let autoSaveInFlight = false;
let autoSaveAgain = false;
let autoSavePromise: Promise<void> | null = null;
let saveSequence = 0;
const undoStack: GraphHistoryEntry[] = [];
const redoStack: GraphHistoryEntry[] = [];





function currentGraph(): GraphDocument {
  return state.graph ?? fallbackGraph;
}








function buildBlocks(graph: GraphDocument): SlotBlock[] {
  const metricsCache = new Map<string, BlockMetrics>();
  return graph.nodes.map((nodeItem) => {
    const kind = blockKind(nodeItem.type);
    const position = nodeItem.position ?? fallbackPosition(nodeItem.id);
    const size = blockMetrics(graph, nodeItem, metricsCache);
    return {
      id: nodeItem.id,
      kind,
      branch: branchForNode(graph, nodeItem),
      type: nodeTypeLabel(nodeItem.type),
      title: nodeItem.displayName || nodeItem.id,
      summary: nodeSummary(nodeItem),
      x: position.x,
      y: position.y,
      width: size.width,
      height: size.height,
      inputY: size.inputY,
      outputOffsets: size.outputOffsets,
      selected: nodeItem.id === state.selectedNodeId,
    };
  });
}

function buildJoins(graph: GraphDocument, blocks: SlotBlock[]): SlotJoin[] {
  const blockById = new Map(blocks.map((block) => [block.id, block]));
  return connectedGraphEdges(graph)
    .map((graphEdge): SlotJoin | null => {
      const source = blockById.get(graphEdge.sourceNodeId);
      const target = blockById.get(graphEdge.targetNodeId);
      if (!source || !target) {
        return null;
      }
      const tone = graphEdge.sourceSlotId === 'pass' ? 'pass' : graphEdge.sourceSlotId === 'fail' ? 'fail' : 'normal';
      return {
        id: graphEdge.id,
        from: source.id,
        to: target.id,
        branch: tone === 'fail' ? 'fail' : tone === 'pass' ? 'pass' : target.branch,
        x: target.x - 2,
        y: target.y + (target.inputY ?? normalBlockHeight / 2) - 15,
        width: 20,
        tone,
      };
    })
    .filter((join): join is SlotJoin => join !== null);
}

function updateWorldSize(blocks: SlotBlock[]): void {
  const maxRight = blocks.reduce((right, block) => Math.max(right, block.x + block.width), 0);
  const maxBottom = blocks.reduce((bottom, block) => Math.max(bottom, block.y + block.height), 0);
  world.width = Math.max(2160, Math.ceil(maxRight + 260));
  world.height = Math.max(620, Math.ceil(maxBottom + 120));
}




function renderApp(): void {
  if (!app) {
    return;
  }

  const graph = currentGraph();
  const blocks = buildBlocks(graph);
  updateWorldSize(blocks);
  const joins = buildJoins(graph, blocks);
  const selectedNode = selectedNodeFrom(graph);
  const validationItems = validationList(state, autoSaveInFlight);
  const graphCount = graph.nodes.length;

  app.innerHTML = `
    <section class="workspace" aria-label="PixelLogic 槽位式横向积木流">
      <header class="topbar">
        <div class="brand">
          <span class="mark" aria-hidden="true"></span>
          <div>
            <strong>PixelLogic</strong>
            <small>当前流程：${escapeHtml(graph.displayName)}</small>
          </div>
        </div>
        <nav class="top-actions" aria-label="工作台操作">
          <span class="api-pill ${state.apiStatus}" data-api-status aria-live="polite">${escapeHtml(apiStatusText())}</span>
          <button type="button" class="ghost-button" data-history-action="undo" title="Ctrl+Z" ${canUndo() ? '' : 'disabled'}>上一步</button>
          <button type="button" class="ghost-button" data-history-action="redo" title="Ctrl+Y / Ctrl+Shift+Z" ${canRedo() ? '' : 'disabled'}>下一步</button>
          <button type="button" class="run-button" data-api-action="start" ${apiBusyAttr()}>测试运行</button>
          <button type="button" class="ghost-button" data-action="fit">适应视图</button>
          <button type="button" class="ghost-button" data-action="center">回到中心</button>
        </nav>
      </header>

      <aside class="left-rail" aria-label="流程列表和积木库">
        <section>
          <div class="panel-title">
            <span>流程列表</span>
            <button type="button" class="tiny-button">新建</button>
          </div>
          <button type="button" class="graph-item active">${escapeHtml(graph.displayName)} <small>${graphCount} 个积木</small></button>
        </section>

        <section>
          <div class="panel-title"><span>积木库</span></div>
          <div class="library-grid">
            <button type="button" data-library-kind="trigger">触发器</button>
            <button type="button" data-library-kind="condition">条件</button>
            <button type="button" data-library-kind="action">动作</button>
            <button type="button" data-library-kind="state">状态</button>
            <button type="button" data-library-kind="timer">计时器</button>
            <button type="button" data-library-kind="debug">调试</button>
          </div>
        </section>

        <section class="quick-start">
          <div class="panel-title"><span>当前版本</span></div>
          <button type="button">Committed ${escapeHtml(shortFingerprint(state.committedGraph?.fingerprint ?? graph.fingerprint))}</button>
          <button type="button" data-draft-status>${draftStatusText(state, autoSaveInFlight)}</button>
          <button type="button" data-dirty-status>${state.dirty ? '等待自动保存' : '无本地改动'}</button>
        </section>
      </aside>

      <main class="graph-stage" aria-label="逻辑画布">
        <div class="stage-head">
          <div>
            <p class="eyebrow">逻辑画布</p>
            <h1>槽位式横向积木流</h1>
          </div>
          <div class="canvas-tools" aria-label="画布状态">
            <span>缩放 <b data-zoom>92%</b></span>
            <span class="actor-chip">${escapeHtml(state.demoActor)}</span>
            <button type="button" data-action="focus">聚焦选中</button>
          </div>
        </div>
        <section class="canvas-viewport" aria-label="可拖动画布">
          <div class="flow-world" style="width:${world.width}px; height:${world.height}px">
            ${joins.map(renderSlotJoin).join('')}
            ${blocks.map((block) => renderBlock(block, state.recentNodeId)).join('')}
          </div>
          <div class="drag-hint" data-drag-hint aria-live="polite"></div>
        </section>
      </main>

      <aside class="right-panel" aria-label="选中积木信息">
        <div class="panel-title">
          <span>选中积木</span>
          <b>${selectedNode ? escapeHtml(nodeTypeLabel(selectedNode.type)) : '未选中'}</b>
        </div>
        ${selectedNode ? renderNodeInfo(selectedNode, graph, state.selectedNodeId) : '<section class="info-card">单击积木选中，拖动积木移动，双击积木编辑。</section>'}
        <section class="preview-card">
          <b>API 状态</b>
          <p>${escapeHtml(state.statusMessage)}</p>
        </section>
        <section class="validation-card">
          <b>最后动作</b>
          <p data-last-action>${escapeHtml(state.lastAction)}</p>
        </section>
        ${state.error ? `<section class="api-error" role="alert">${escapeHtml(state.error)}</section>` : ''}
      </aside>

      <footer class="bottom-dock" aria-label="验证问题和执行记录">
        <section>
          <div class="panel-title"><span>自动检查</span><b data-validation-title>${validationTitle(state, autoSaveInFlight)}</b></div>
          <ul class="issue-list" data-issue-list>
            <li><span class="${state.apiStatus === 'online' ? 'ok' : 'warn'}"></span>${escapeHtml(state.statusMessage)}</li>
            ${uncommittedNotice(state) ? `<li><span class="warn"></span>${escapeHtml(uncommittedNotice(state))}</li>` : ''}
            ${validationItems}
          </ul>
        </section>
        <section>
          <div class="panel-title"><span>执行记录</span><b>${state.latestTrace ? escapeHtml(shortTraceId(state.latestTrace.id)) : '无'}</b></div>
          <ol class="trace-list">
            ${renderTrace(state.latestTrace)}
          </ol>
        </section>
      </footer>
      ${state.editorOpen && selectedNode ? renderEditorModal(selectedNode, { editorClosing: state.editorClosing, error: state.error, hasValidation: Boolean(state.validation), modalIssue: state.error || validationSummaryText(state) }) : ''}
    </section>
  `;

  bindInteractions();
  setTransform();
  focusEditor();
}














function setTransform(): void {
  const worldEl = document.querySelector<HTMLDivElement>('.flow-world');
  const zoomText = document.querySelector<HTMLElement>('[data-zoom]');

  if (!worldEl) {
    return;
  }

  worldEl.style.transform = `translate(${offsetX}px, ${offsetY}px) scale(${scale})`;

  if (zoomText) {
    zoomText.textContent = `${Math.round(scale * 100)}%`;
  }
}

function fitView(): void {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');

  if (!viewport) {
    return;
  }

  const rect = viewport.getBoundingClientRect();
  scale = Math.min(1, (rect.width - 72) / world.width, (rect.height - 56) / world.height);
  offsetX = Math.max(26, (rect.width - world.width * scale) / 2);
  offsetY = Math.max(22, (rect.height - world.height * scale) / 2);
  setTransform();
}

function centerView(): void {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');

  if (!viewport) {
    return;
  }

  const rect = viewport.getBoundingClientRect();
  scale = 0.78;
  offsetX = rect.width > 1200 ? -28 : 28;
  offsetY = Math.max(24, (rect.height - world.height * scale) / 2);
  setTransform();
}

function focusSelectedBlock(): void {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');
  const selected = buildBlocks(currentGraph()).find((block) => block.selected);

  if (!viewport || !selected) {
    return;
  }

  const rect = viewport.getBoundingClientRect();
  scale = Math.max(0.76, Math.min(1.02, scale));
  offsetX = rect.width / 2 - (selected.x + selected.width / 2) * scale;
  offsetY = rect.height / 2 - (selected.y + selected.height / 2) * scale;
  setTransform();
}

function clearFocus(): void {
  document.querySelectorAll('.logic-block, .slot-join').forEach((item) => {
    item.classList.remove('is-related');
  });
}

function markJoinFocus(joinEl: HTMLElement): void {
  clearFocus();
  joinEl.classList.add('is-related');
  document.querySelector(`[data-block="${joinEl.dataset.from}"]`)?.classList.add('is-related');
  document.querySelector(`[data-block="${joinEl.dataset.to}"]`)?.classList.add('is-related');
}

function markSelectedFocus(): void {
  const selected = state.selectedNodeId;

  document.querySelector(`[data-block="${selected}"]`)?.classList.add('is-related');
  document.querySelectorAll<HTMLElement>('.slot-join').forEach((joinEl) => {
    if (joinEl.dataset.from === selected || joinEl.dataset.to === selected) {
      joinEl.classList.add('is-related');
    }
  });
}

function bindInteractions(): void {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');

  if (!viewport) {
    return;
  }

  viewport.addEventListener('pointerdown', (event) => {
    const target = event.target as HTMLElement;
    const blockEl = target.closest<HTMLElement>('.logic-block');
    if (blockEl?.dataset.block) {
      event.preventDefault();
      beginBlockPointerDown(event, blockEl.dataset.block, viewport);
      return;
    }

    if (target.closest('.slot-join, button, input, select')) {
      return;
    }

    isPanning = true;
    panStart = { x: event.clientX, y: event.clientY };
    panOffset = { x: offsetX, y: offsetY };
    viewport.classList.add('is-dragging');
    viewport.setPointerCapture(event.pointerId);
  });

  viewport.addEventListener('pointermove', (event) => {
    if (activeBlockDrag?.pointerId === event.pointerId) {
      moveBlockDrag(event);
      return;
    }

    if (!isPanning) {
      return;
    }

    offsetX = panOffset.x + event.clientX - panStart.x;
    offsetY = panOffset.y + event.clientY - panStart.y;
    setTransform();
  });

  viewport.addEventListener('pointerup', (event) => {
    if (activeBlockDrag?.pointerId === event.pointerId) {
      endBlockDrag(event, viewport);
      return;
    }

    isPanning = false;
    viewport.classList.remove('is-dragging');
    if (viewport.hasPointerCapture(event.pointerId)) {
      viewport.releasePointerCapture(event.pointerId);
    }
  });

  viewport.addEventListener('pointercancel', (event) => {
    if (activeBlockDrag?.pointerId === event.pointerId) {
      cancelBlockDrag(viewport);
      return;
    }
    isPanning = false;
    viewport.classList.remove('is-dragging');
  });

  viewport.addEventListener(
    'wheel',
    (event) => {
      event.preventDefault();
      const previous = scale;
      const next = Math.min(1.22, Math.max(0.58, scale + (event.deltaY > 0 ? -0.06 : 0.06)));
      const rect = viewport.getBoundingClientRect();
      const pointerX = event.clientX - rect.left;
      const pointerY = event.clientY - rect.top;
      const worldX = (pointerX - offsetX) / previous;
      const worldY = (pointerY - offsetY) / previous;
      scale = next;
      offsetX = pointerX - worldX * scale;
      offsetY = pointerY - worldY * scale;
      setTransform();
    },
    { passive: false },
  );

  document.querySelector('[data-action="fit"]')?.addEventListener('click', fitView);
  document.querySelector('[data-action="center"]')?.addEventListener('click', centerView);
  document.querySelector('[data-action="focus"]')?.addEventListener('click', focusSelectedBlock);
  document.querySelector('[data-api-action="start"]')?.addEventListener('click', () => void startTest());
  document.querySelector('[data-history-action="undo"]')?.addEventListener('click', undoGraphEdit);
  document.querySelector('[data-history-action="redo"]')?.addEventListener('click', redoGraphEdit);
  document.querySelector('[data-graph-action="disconnect-input"]')?.addEventListener('click', disconnectSelectedInput);
  document.querySelector('[data-graph-action="delete-selected"]')?.addEventListener('click', deleteSelectedNode);
  document.querySelector('[data-modal-action="close"]')?.addEventListener('click', requestCloseEditor);
  document.querySelector('[data-modal-action="cancel"]')?.addEventListener('click', requestCloseEditor);
  document.querySelectorAll<HTMLButtonElement>('[data-library-kind]').forEach((buttonEl) => {
    buttonEl.addEventListener('click', () => {
      const kind = buttonEl.dataset.libraryKind as LibraryKind | undefined;
      if (kind) {
        addLibraryBlock(kind);
      }
    });
  });
  document.querySelector('.editor-overlay')?.addEventListener('pointerdown', (event) => {
    if ((event.target as HTMLElement).hasAttribute('data-modal-overlay')) {
      requestCloseEditor();
    }
  });

  document.onkeydown = (event) => {
    if (isUndoShortcut(event)) {
      event.preventDefault();
      undoGraphEdit();
      return;
    }
    if (isRedoShortcut(event)) {
      event.preventDefault();
      redoGraphEdit();
      return;
    }
    if (event.key === 'Escape' && state.editorOpen) {
      event.preventDefault();
      requestCloseEditor();
    }
    if (event.key === 'Tab' && state.editorOpen) {
      trapEditorFocus(event);
    }
  };
  window.onbeforeunload = state.dirty || state.hasDraft || autoSaveInFlight ? () => '还有修改正在自动保存，确定要离开吗？' : null;

  document.querySelectorAll<HTMLInputElement | HTMLSelectElement>('[data-node-field], [data-config-key]').forEach((inputEl) => {
    if (inputEl instanceof HTMLSelectElement) {
      inputEl.addEventListener('change', () => updateSelectedNode(inputEl));
      return;
    }
    inputEl.addEventListener('input', () => updateSelectedNode(inputEl));
  });
  document.querySelectorAll<HTMLButtonElement>('[data-config-value]').forEach((buttonEl) => {
    buttonEl.addEventListener('click', () => {
      if (buttonEl.dataset.configKey && buttonEl.dataset.configValue) {
        updateSelectedNodeValue(buttonEl.dataset.configKey, buttonEl.dataset.configValue);
        document.querySelectorAll<HTMLButtonElement>(`[data-config-key="${buttonEl.dataset.configKey}"][data-config-value]`).forEach((item) => {
          item.setAttribute('aria-pressed', String(item === buttonEl));
        });
      }
    });
  });

  document.querySelectorAll<HTMLElement>('.slot-join').forEach((joinEl) => {
    joinEl.addEventListener('mouseenter', () => markJoinFocus(joinEl));
    joinEl.addEventListener('mouseleave', () => {
      clearFocus();
      markSelectedFocus();
    });
  });

  markSelectedFocus();
}

function beginBlockPointerDown(event: PointerEvent, nodeId: string, viewport: HTMLElement): void {
  const graph = currentGraph();
  if (!graph.nodes.some((nodeItem) => nodeItem.id === nodeId)) {
    return;
  }

  if (event.detail >= 2) {
    lastBlockClick = null;
    openEditor(nodeId);
    return;
  }

  state.selectedNodeId = nodeId;
  state.recentNodeId = null;
  const groupIds = downstreamNodeIds(graph, nodeId);
  const startPositions = new Map<string, GraphPosition>();
  groupIds.forEach((id) => startPositions.set(id, nodePosition(graph, id)));
  activeBlockDrag = {
    pointerId: event.pointerId,
    rootId: nodeId,
    groupIds,
    started: false,
    startClient: { x: event.clientX, y: event.clientY },
    startWorld: pointerToWorld(event),
    startPositions,
    previewPositions: new Map(startPositions),
    joins: buildJoins(graph, buildBlocks(graph)),
    candidate: null,
  };
  viewport.setPointerCapture(event.pointerId);
}

function moveBlockDrag(event: PointerEvent): void {
  const drag = activeBlockDrag;
  if (!drag) {
    return;
  }

  const clientDx = event.clientX - drag.startClient.x;
  const clientDy = event.clientY - drag.startClient.y;
  if (!drag.started && Math.hypot(clientDx, clientDy) < dragThreshold) {
    return;
  }

  if (!drag.started) {
    drag.started = true;
    lastBlockClick = null;
    state.selectedNodeId = drag.rootId;
    state.recentNodeId = null;
    document.querySelector('.canvas-viewport')?.classList.add('is-block-dragging');
    drag.groupIds.forEach((id) => document.querySelector<HTMLElement>(`[data-block="${id}"]`)?.classList.add('is-chain-dragging'));
    document.querySelector<HTMLElement>(`[data-block="${drag.rootId}"]`)?.classList.add('is-drag-root', 'selected');
    clearFocus();
    setDragHint('拖动整链中，靠近两个积木之间会自动吸附插入。', 'active');
  }

  const current = pointerToWorld(event);
  const worldDx = current.x - drag.startWorld.x;
  const worldDy = current.y - drag.startWorld.y;
  drag.previewPositions = new Map();
  drag.groupIds.forEach((id) => {
    const start = drag.startPositions.get(id);
    if (!start) {
      return;
    }
    const next = {
      x: Math.round(start.x + worldDx),
      y: Math.round(start.y + worldDy),
    };
    drag.previewPositions.set(id, next);
    const blockEl = document.querySelector<HTMLElement>(`[data-block="${id}"]`);
    if (blockEl) {
      blockEl.style.left = `${next.x}px`;
      blockEl.style.top = `${next.y}px`;
    }
  });

  drag.candidate = findInsertCandidate(currentGraph(), drag);
  renderInsertPreview(drag);
}

function endBlockDrag(event: PointerEvent, viewport: HTMLElement): void {
  const drag = activeBlockDrag;
  if (!drag) {
    return;
  }

  activeBlockDrag = null;
  clearInsertPreview();
  setDragHint('', '');
  viewport.classList.remove('is-block-dragging');
  if (viewport.hasPointerCapture(event.pointerId)) {
    viewport.releasePointerCapture(event.pointerId);
  }

  if (!drag.started) {
    selectOrOpenBlock(drag.rootId);
    return;
  }

  snapDraggedGroupToCandidate(currentGraph(), drag);
  const nextGraph = cloneGraph(currentGraph());
  nextGraph.nodes = nextGraph.nodes.map((nodeItem) => {
    const nextPosition = drag.previewPositions.get(nodeItem.id);
    return nextPosition ? { ...nodeItem, position: nextPosition } : nodeItem;
  });
  nextGraph.edges = connectedGraphEdges(nextGraph);
  const inserted = drag.candidate?.valid ? insertDraggedGroup(nextGraph, drag) : false;
  if (inserted) {
    makeInsertionGap(nextGraph, drag);
  }
  const actionText = inserted
    ? connectedActionText(drag.candidate)
    : '位置已更新，正在自动保存。';
  applyGraphEdit(nextGraph, actionText, { selectedNodeId: drag.rootId, recentNodeId: null });
}


function selectOrOpenBlock(nodeId: string): void {
  const now = window.performance.now();
  const isDoubleClick = lastBlockClick?.nodeId === nodeId && now - lastBlockClick.time <= doubleClickMs;

  if (isDoubleClick) {
    lastBlockClick = null;
    openEditor(nodeId);
    return;
  }

  lastBlockClick = { nodeId, time: now };
  state.selectedNodeId = nodeId;
  state.recentNodeId = null;
  state.error = '';
  renderApp();
}

function cancelBlockDrag(viewport: HTMLElement): void {
  activeBlockDrag = null;
  clearInsertPreview();
  setDragHint('', '');
  viewport.classList.remove('is-block-dragging');
  renderApp();
}

function pointerToWorld(event: PointerEvent): GraphPosition {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');
  if (!viewport) {
    return { x: 0, y: 0 };
  }
  const rect = viewport.getBoundingClientRect();
  return {
    x: (event.clientX - rect.left - offsetX) / scale,
    y: (event.clientY - rect.top - offsetY) / scale,
  };
}




function canUndo(): boolean {
  return undoStack.length > 0;
}

function canRedo(): boolean {
  return redoStack.length > 0;
}

function graphHistorySnapshot(): GraphHistoryEntry {
  return {
    graph: cloneGraph(currentGraph()),
    selectedNodeId: state.selectedNodeId,
    recentNodeId: state.recentNodeId,
    lastAction: state.lastAction,
  };
}

function rememberGraphState(): void {
  pushHistory(undoStack, graphHistorySnapshot());
  redoStack.length = 0;
}

function pushHistory(stack: GraphHistoryEntry[], entry: GraphHistoryEntry): void {
  stack.push(entry);
  while (stack.length > historyLimit) {
    stack.shift();
  }
}

function resetGraphHistory(): void {
  undoStack.length = 0;
  redoStack.length = 0;
  graphVersion = 0;
  if (autoSaveTimer !== null) {
    window.clearTimeout(autoSaveTimer);
    autoSaveTimer = null;
  }
}

function applyGraphEdit(
  graph: GraphDocument,
  lastAction: string,
  options: { selectedNodeId?: string; recentNodeId?: string | null; refreshOnly?: boolean } = {},
): void {
  const nextGraph = normalizeConditionBranchLayout(graph);
  rememberGraphState();
  graphVersion += 1;
  state.graph = nextGraph;
  state.dirty = true;
  state.validation = null;
  state.error = '';
  state.lastAction = lastAction;
  if (options.selectedNodeId !== undefined) {
    state.selectedNodeId = options.selectedNodeId;
  }
  if ('recentNodeId' in options) {
    state.recentNodeId = options.recentNodeId ?? null;
  }
  scheduleAutoSave();
  if (options.refreshOnly) {
    refreshDraftIndicators();
  } else {
    renderApp();
  }
}

function restoreGraphHistory(entry: GraphHistoryEntry, lastAction: string): void {
  graphVersion += 1;
  state.graph = normalizeConditionBranchLayout(entry.graph);
  state.selectedNodeId = entry.selectedNodeId;
  state.recentNodeId = entry.recentNodeId;
  ensureSelectedNode();
  state.dirty = true;
  state.validation = null;
  state.error = '';
  state.lastAction = lastAction;
  scheduleAutoSave();
  renderApp();
}

function undoGraphEdit(): void {
  const previous = undoStack.pop();
  if (!previous) {
    return;
  }
  pushHistory(redoStack, graphHistorySnapshot());
  restoreGraphHistory(previous, `已撤销：${previous.lastAction}`);
}

function redoGraphEdit(): void {
  const next = redoStack.pop();
  if (!next) {
    return;
  }
  pushHistory(undoStack, graphHistorySnapshot());
  restoreGraphHistory(next, `已重做：${next.lastAction}`);
}


function isUndoShortcut(event: KeyboardEvent): boolean {
  return !event.isComposing
    && (event.ctrlKey || event.metaKey)
    && !event.altKey
    && !event.shiftKey
    && event.key.toLowerCase() === 'z';
}

function isRedoShortcut(event: KeyboardEvent): boolean {
  const key = event.key.toLowerCase();
  return !event.isComposing
    && (event.ctrlKey || event.metaKey)
    && !event.altKey
    && (key === 'y' || (event.shiftKey && key === 'z'));
}



















function renderInsertPreview(drag: BlockDrag): void {
  const candidate = drag.candidate;
  clearInsertPreview();
  if (!candidate) {
    setDragHint('靠近两个积木之间会自动吸附插入。', 'active');
    return;
  }

  if (candidate.kind === 'insert') {
    const joinEl = document.querySelector<HTMLElement>(`[data-join="${candidate.join.id}"]`);
    joinEl?.classList.add(candidate.valid ? 'insert-target' : 'insert-invalid');
    if (candidate.valid) {
      markInsertSplitGroups(graphWithPreviewPositions(currentGraph(), drag), candidate);
    }
  }
  document.querySelector<HTMLElement>(`[data-block="${candidate.join.from}"]`)?.classList.add('is-related');
  document.querySelector<HTMLElement>(`[data-block="${candidate.join.to}"]`)?.classList.add('is-related');
  setDragHint(candidate.message, candidate.valid ? 'valid' : 'invalid');
}

function markInsertSplitGroups(graph: GraphDocument, candidate: Extract<InsertCandidate, { kind: 'insert' }>): void {
  const leftSide = new Set(connectedComponentNodeIds(graph, candidate.edge.sourceNodeId, candidate.edge.id));
  const rightSide = new Set(connectedComponentNodeIds(graph, candidate.edge.targetNodeId, candidate.edge.id));
  const overlap = new Set(Array.from(leftSide).filter((nodeId) => rightSide.has(nodeId)));

  leftSide.forEach((nodeId) => {
    if (!overlap.has(nodeId)) {
      document.querySelector<HTMLElement>(`[data-block="${nodeId}"]`)?.classList.add('is-insert-split-left');
    }
  });
  rightSide.forEach((nodeId) => {
    if (!overlap.has(nodeId)) {
      document.querySelector<HTMLElement>(`[data-block="${nodeId}"]`)?.classList.add('is-insert-split-right');
    }
  });
}

function clearInsertPreview(): void {
  document.querySelectorAll('.slot-join.insert-target, .slot-join.insert-invalid').forEach((item) => {
    item.classList.remove('insert-target', 'insert-invalid');
  });
  document.querySelectorAll('.logic-block.is-insert-split-left, .logic-block.is-insert-split-right').forEach((item) => {
    item.classList.remove('is-insert-split-left', 'is-insert-split-right');
  });
  clearFocus();
}

function setDragHint(message: string, tone: string): void {
  const hint = document.querySelector<HTMLElement>('[data-drag-hint]');
  if (!hint) {
    return;
  }
  hint.textContent = message;
  hint.className = `drag-hint${tone ? ` ${tone}` : ''}`;
}





function addLibraryBlock(kind: LibraryKind): void {
  const graph = cloneGraph(currentGraph());
  const nodeItem = createLibraryNode(kind, visibleDropPosition(kind));
  graph.nodes.push(nodeItem);
  applyGraphEdit(graph, `已新增“${nodeTypeLabel(nodeItem.type)}”，正在自动保存。`, {
    selectedNodeId: nodeItem.id,
    recentNodeId: nodeItem.id,
  });
}

function createLibraryNode(kind: LibraryKind, position: GraphPosition): GraphNode {
  const graph = currentGraph();
  switch (kind) {
    case 'trigger':
      return node(uniqueNodeId('manual-trigger', graph), 'MANUAL_TRIGGER', '手动触发', {}, position, [out('started')]);
    case 'condition':
      return node(
        uniqueNodeId('condition', graph),
        'STATE_COMPARE_CONDITION',
        '条件判断',
        { scope: 'PLAYER', key: 'started', valueType: 'BOOLEAN', expected: 'false', missing: 'false' },
        position,
        [input('input'), out('pass'), out('fail')],
      );
    case 'state':
      return node(
        uniqueNodeId('state-set', graph),
        'STATE_SET_ACTION',
        '状态写入',
        { scope: 'PLAYER', key: 'started', valueType: 'BOOLEAN', value: 'true' },
        position,
        [input('input'), out('done')],
      );
    case 'timer':
      return node(uniqueNodeId('timer', graph), 'TIMER_START_ACTION', '计时器', { durationSeconds: '30' }, position, [
        input('input'),
        out('timer_completed'),
      ]);
    case 'debug':
      return node(uniqueNodeId('debug', graph), 'DEBUG_LOG_ACTION', '调试记录', { message: '调试记录' }, position, [
        input('input'),
        out('done'),
      ]);
    case 'action':
    default:
      return node(uniqueNodeId('message', graph), 'MESSAGE_ACTION', '发送消息', { message: '新消息' }, position, [
        input('input'),
        out('done'),
      ]);
  }
}

function uniqueNodeId(prefix: string, graph: GraphDocument): string {
  let index = graph.nodes.length + 1;
  let id = `${prefix}-${index}`;
  while (graph.nodes.some((nodeItem) => nodeItem.id === id)) {
    index += 1;
    id = `${prefix}-${index}`;
  }
  return id;
}

function visibleDropPosition(kind: LibraryKind): GraphPosition {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');
  const size = blockSize(kind);
  if (!viewport) {
    return { x: 84, y: 88 };
  }
  const rect = viewport.getBoundingClientRect();
  const x = (rect.width / 2 - offsetX) / scale - size.width / 2;
  const y = (rect.height / 2 - offsetY) / scale - size.height / 2;
  return {
    x: Math.max(28, Math.round(x)),
    y: Math.max(28, Math.round(y)),
  };
}

function disconnectSelectedInput(): void {
  const graph = cloneGraph(currentGraph());
  const before = graph.edges.length;
  graph.edges = graph.edges.filter((graphEdge) => graphEdge.targetNodeId !== state.selectedNodeId);
  if (graph.edges.length === before) {
    return;
  }
  applyGraphEdit(graph, '已断开该积木的输入连接，正在自动保存。');
}

function deleteSelectedNode(): void {
  const graph = cloneGraph(currentGraph());
  if (Object.values(graph.triggerEntries).includes(state.selectedNodeId)) {
    state.error = '入口积木不能删除。';
    refreshDraftIndicators();
    return;
  }
  const before = graph.nodes.length;
  graph.nodes = graph.nodes.filter((nodeItem) => nodeItem.id !== state.selectedNodeId);
  if (graph.nodes.length === before) {
    return;
  }
  graph.edges = graph.edges.filter((graphEdge) => graphEdge.sourceNodeId !== state.selectedNodeId && graphEdge.targetNodeId !== state.selectedNodeId);
  applyGraphEdit(graph, '已删除积木和相关连接，正在自动保存。', {
    selectedNodeId: graph.nodes[0]?.id ?? '',
    recentNodeId: null,
  });
}

function openEditor(nodeId: string): void {
  state.selectedNodeId = nodeId;
  state.editorOpen = true;
  state.editorClosing = false;
  renderApp();
}

function requestCloseEditor(): void {
  closeEditor();
}

function closeEditor(): void {
  state.editorClosing = true;
  const overlayEl = document.querySelector<HTMLElement>('.editor-overlay');
  if (overlayEl) {
    overlayEl.classList.add('is-closing');
  }
  window.setTimeout(() => {
    state.editorOpen = false;
    state.editorClosing = false;
    renderApp();
  }, 160);
}

function focusEditor(): void {
  if (!state.editorOpen) {
    return;
  }
  window.setTimeout(() => {
    const target = document.querySelector<HTMLElement>('.editor-dialog input, .editor-dialog select, #block-editor-title');
    target?.focus();
  }, 0);
}

function trapEditorFocus(event: KeyboardEvent): void {
  const focusSelector = '.editor-dialog button:not([disabled]), .editor-dialog input:not([disabled]), .editor-dialog select:not([disabled]), #block-editor-title';
  const focusables = Array.from(
    document.querySelectorAll<HTMLElement>(focusSelector),
  ).filter((item) => item.offsetParent !== null);
  if (focusables.length === 0) {
    return;
  }
  const first = focusables[0];
  const last = focusables[focusables.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

function updateSelectedNode(inputEl: HTMLInputElement | HTMLSelectElement): void {
  if (inputEl.dataset.nodeField === 'displayName') {
    updateSelectedNodeValue('displayName', inputEl.value, 'node');
  }
  if (inputEl.dataset.configKey) {
    updateSelectedNodeValue(inputEl.dataset.configKey, inputEl.value);
  }
}

function updateSelectedNodeValue(key: string, value: string, target: 'config' | 'node' = 'config'): void {
  const graph = currentGraph();
  const selected = selectedNodeFrom(graph);
  if (!selected) {
    return;
  }
  const nextGraph = cloneGraph(graph);
  const nextNode = nextGraph.nodes.find((item) => item.id === selected.id);
  if (!nextNode) {
    return;
  }

  if (target === 'node' && key === 'displayName') {
    if (nextNode.displayName === value) {
      return;
    }
    nextNode.displayName = value;
  } else {
    if (nextNode.config[key] === value) {
      return;
    }
    nextNode.config[key] = value;
  }
  applyGraphEdit(nextGraph, '内容已修改，正在自动保存。', { refreshOnly: true });
}

function refreshDraftIndicators(): void {
  const apiStatus = document.querySelector<HTMLElement>('[data-api-status]');
  const draftStatus = document.querySelector<HTMLElement>('[data-draft-status]');
  const dirtyStatus = document.querySelector<HTMLElement>('[data-dirty-status]');
  const lastAction = document.querySelector<HTMLElement>('[data-last-action]');
  const validationTitleEl = document.querySelector<HTMLElement>('[data-validation-title]');
  const issueList = document.querySelector<HTMLElement>('[data-issue-list]');
  const modalSummary = document.querySelector<HTMLElement>('[data-modal-summary]');
  const modalError = document.querySelector<HTMLElement>('[data-modal-error]');
  const undoButton = document.querySelector<HTMLButtonElement>('[data-history-action="undo"]');
  const redoButton = document.querySelector<HTMLButtonElement>('[data-history-action="redo"]');

  if (apiStatus) {
    apiStatus.textContent = apiStatusText();
    apiStatus.className = `api-pill ${state.apiStatus}`;
  }
  if (draftStatus) {
    draftStatus.textContent = draftStatusText(state, autoSaveInFlight);
  }
  if (dirtyStatus) {
    dirtyStatus.textContent = state.dirty ? '等待自动保存' : '无本地改动';
  }
  if (lastAction) {
    lastAction.textContent = state.lastAction;
  }
  if (validationTitleEl) {
    validationTitleEl.textContent = validationTitle(state, autoSaveInFlight);
  }
  if (issueList) {
    issueList.innerHTML = `
      <li><span class="${state.apiStatus === 'online' ? 'ok' : 'warn'}"></span>${escapeHtml(state.statusMessage)}</li>
      ${uncommittedNotice(state) ? `<li><span class="warn"></span>${escapeHtml(uncommittedNotice(state))}</li>` : ''}
      ${validationList(state, autoSaveInFlight)}
    `;
  }
  if (modalSummary) {
    const selected = selectedNodeFrom(currentGraph());
    modalSummary.textContent = selected ? nodeSummary(selected) : '';
  }
  if (modalError) {
    modalError.textContent = state.error || validationSummaryText(state);
  }
  undoButton?.toggleAttribute('disabled', !canUndo());
  redoButton?.toggleAttribute('disabled', !canRedo());
  window.onbeforeunload = state.dirty || state.hasDraft || autoSaveInFlight ? () => '还有修改正在自动保存，确定要离开吗？' : null;
}

function scheduleAutoSave(): void {
  if (autoSaveTimer !== null) {
    window.clearTimeout(autoSaveTimer);
  }
  autoSaveTimer = window.setTimeout(() => {
    autoSaveTimer = null;
    void startAutoSave();
  }, autoSaveDelayMs);
}

function startAutoSave(): Promise<void> {
  if (autoSaveInFlight && autoSavePromise) {
    autoSaveAgain = true;
    return autoSavePromise;
  }
  const promise = performAutoSave();
  autoSavePromise = promise;
  void promise.finally(() => {
    if (autoSavePromise === promise) {
      autoSavePromise = null;
    }
  });
  return promise;
}

async function performAutoSave(): Promise<void> {
  if (autoSaveInFlight) {
    autoSaveAgain = true;
    return;
  }
  if (!state.dirty && !state.hasDraft) {
    return;
  }

  const saveVersion = graphVersion;
  autoSaveInFlight = true;
  state.busyAction = '自动保存';
  refreshDraftIndicators();

  try {
    await saveAndCommit({ auto: true, version: saveVersion });
    state.apiStatus = 'online';
    state.statusMessage = 'API 已连接';
  } catch (error) {
    const connected = error instanceof PixelLogicApiError ? error.connected : false;
    state.apiStatus = connected ? 'online' : 'offline';
    state.statusMessage = connected ? 'API 已连接' : 'API 未连接';
    state.error = error instanceof Error ? error.message : 'API 未连接';
    state.lastAction = '自动保存失败，请确认 API 连接或修复检查问题。';
  } finally {
    const changedDuringSave = autoSaveAgain || graphVersion !== saveVersion;
    autoSaveAgain = false;
    autoSaveInFlight = false;
    state.busyAction = null;
    if (changedDuringSave) {
      scheduleAutoSave();
    }
    if (state.editorOpen) {
      refreshDraftIndicators();
    } else {
      renderApp();
    }
  }
}

async function waitForPendingAutoSave(): Promise<void> {
  if (autoSaveTimer !== null) {
    window.clearTimeout(autoSaveTimer);
    autoSaveTimer = null;
  }
  if (autoSavePromise) {
    await autoSavePromise;
  }
}

async function loadGraph(): Promise<void> {
  await runAction('加载图', async () => {
    const graphResponse = await api(`/api/pixellogic/graphs/${graphId}`);
    if (!graphResponse.graph) {
      throw new Error('API 未返回 graph。');
    }
    state.committedGraph = normalizeConditionBranchLayout(graphResponse.graph);
    state.graph = state.committedGraph;
    state.validation = graphResponse.validation ?? null;
    state.hasDraft = graphResponse.hasDraft ?? false;

    const draftResponse = await api(`/api/pixellogic/graphs/${graphId}/draft`);
    if (draftResponse.graph) {
      state.graph = normalizeConditionBranchLayout(draftResponse.graph);
      state.hasDraft = true;
    }

    ensureSelectedNode();
    resetGraphHistory();
    state.apiStatus = 'online';
    state.statusMessage = 'Graph 已从 API 加载';
    state.lastAction = state.hasDraft ? '已加载上次未保存完成的修改' : '已加载已保存版本';
  });
}

async function saveAndCommit(options: { auto?: boolean; version?: number; sequence?: number } = {}): Promise<boolean> {
  const saveVersion = options.version ?? graphVersion;
  const sequence = options.sequence ?? ++saveSequence;
  const isCurrentSave = () => graphVersion === saveVersion && saveSequence === sequence;
  syncGraphConnectionsToVisual();

  if (!state.dirty && !state.hasDraft) {
    state.lastAction = options.auto ? '已自动保存。' : '已保存。';
    return true;
  }

  if (state.dirty) {
    await persistDraft(saveVersion, sequence);
    if (!isCurrentSave()) {
      return false;
    }
  }

  const validationData = await api(`/api/pixellogic/graphs/${graphId}/validate`, { method: 'POST' });
  if (!isCurrentSave()) {
    return false;
  }
  state.validation = validationData.validation ?? null;
  if (!state.validation?.valid) {
    state.lastAction = options.auto ? '自动保存未生效：请修复检查问题。' : '保存失败：请修复验证问题。';
    state.error = validationErrorText(state.validation);
    return false;
  }

  const data = await api(`/api/pixellogic/graphs/${graphId}/commit`, { method: 'POST' });
  if (!isCurrentSave()) {
    return false;
  }
  if (!data.graph) {
    throw new Error('API 未返回已保存 graph。');
  }
  state.graph = data.graph;
  state.committedGraph = data.graph;
  state.validation = data.validation ?? null;
  state.hasDraft = false;
  state.dirty = false;
  state.lastAction = options.auto ? '已自动保存并生效。' : '已保存并生效。';
  return true;
}

function syncGraphConnectionsToVisual(): void {
  const current = currentGraph();
  const graph = normalizeConditionBranchLayout(current);
  const connectedEdges = connectedGraphEdges(graph);
  const samePositions = graph.nodes.every((nodeItem) => {
    const currentNode = current.nodes.find((item) => item.id === nodeItem.id);
    const currentPosition = currentNode?.position ?? (currentNode ? fallbackPosition(currentNode.id) : null);
    const nextPosition = nodeItem.position ?? fallbackPosition(nodeItem.id);
    return currentPosition !== null && currentPosition.x === nextPosition.x && currentPosition.y === nextPosition.y;
  });
  if (connectedEdges.length === graph.edges.length && samePositions) {
    return;
  }

  state.graph = { ...cloneGraph(graph), edges: connectedEdges.map((graphEdge) => ({ ...graphEdge })) };
  state.dirty = true;
  state.validation = null;
}

async function persistDraft(saveVersion = graphVersion, sequence = saveSequence): Promise<ApiResponse> {
  const graphToSave = cloneGraph(currentGraph());
  const data = await api(`/api/pixellogic/graphs/${graphId}/draft`, {
    method: 'PUT',
    body: JSON.stringify({ graph: graphToSave }),
  });
  if (!data.graph) {
    throw new Error('API 未返回已保存内容。');
  }
  state.hasDraft = true;
  if (graphVersion === saveVersion && saveSequence === sequence) {
    state.graph = data.graph;
    state.dirty = false;
    state.validation = null;
  }
  return data;
}

async function startTest(): Promise<void> {
  await runAction('测试运行', async () => {
    await waitForPendingAutoSave();
    if (state.dirty || state.hasDraft) {
      const saved = await saveAndCommit({ version: graphVersion });
      if (!saved) {
        return;
      }
    }
    await api('/api/pixellogic/test/reset', { method: 'POST' });
    const data = await api('/api/pixellogic/test/start', { method: 'POST' });
    state.apiStatus = 'online';
    state.latestTrace = data.trace ?? null;
    state.lastAction = data.message || '测试运行已执行';
  });
}

async function refreshLatestTrace(showBusy = true): Promise<void> {
  const action = async () => {
    const data = await api('/api/pixellogic/traces/latest');
    state.apiStatus = 'online';
    state.latestTrace = data.trace ?? null;
    state.lastAction = state.latestTrace ? '执行记录已刷新' : state.lastAction;
  };

  if (showBusy) {
    await runAction('刷新记录', action);
  } else {
    await action();
  }
}

async function runAction(label: string, action: () => Promise<void>, options: { renderBusy?: boolean } = {}): Promise<void> {
  state.busyAction = label;
  state.error = '';
  if (options.renderBusy ?? true) {
    renderApp();
  }

  try {
    await action();
  } catch (error) {
    const connected = error instanceof PixelLogicApiError ? error.connected : false;
    state.apiStatus = connected ? 'online' : 'offline';
    state.statusMessage = connected ? 'API 已连接' : 'API 未连接';
    state.error = error instanceof Error ? error.message : 'API 未连接';
  } finally {
    state.busyAction = null;
    if (!state.editorClosing) {
      renderApp();
    }
  }
}












function selectedNodeFrom(graph: GraphDocument): GraphNode | null {
  return graph.nodes.find((nodeItem) => nodeItem.id === state.selectedNodeId) ?? graph.nodes[0] ?? null;
}

function ensureSelectedNode(): void {
  const graph = currentGraph();
  if (!graph.nodes.some((nodeItem) => nodeItem.id === state.selectedNodeId)) {
    state.selectedNodeId = graph.nodes[0]?.id ?? 'condition-started';
  }
}








function apiStatusText(): string {
  if (state.busyAction) {
    return `${state.busyAction}中`;
  }
  if (state.apiStatus === 'online') {
    return 'API 已连接';
  }
  if (state.apiStatus === 'offline') {
    return 'API 未连接';
  }
  return '连接中';
}

function apiBusyAttr(): string {
  return state.busyAction ? 'disabled' : '';
}






export function startPixelLogicApp(): void {
  if (!app) {
    return;
  }
  renderApp();
  centerView();
  void loadGraph().then(() => {
    if (state.apiStatus === 'online') {
      void refreshLatestTrace(false).catch(() => {
        state.apiStatus = 'offline';
        state.statusMessage = 'API 未连接';
        state.error = 'API 未连接，请确认 PixelLogic API server 已启动。';
        renderApp();
      });
    }
  });
}
