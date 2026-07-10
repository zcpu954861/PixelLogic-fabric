import { PixelLogicApiError, api } from '../api/pixelLogicApi';
import { startTestRunPolling, stopTestRunPolling } from '../api/testRunPolling';
import {
  blockKindFromCatalogBlock,
  catalogBlock,
  catalogNodeIdPrefix,
  createCatalogNode,
  fallbackCatalog,
} from '../model/blockCatalog';
import { fallbackGraph, graphId } from '../model/demoGraph';
import {
  applyRichTextStyle,
  normalizeRichTextColor,
  richTextSelectionState,
  serializeRichText,
  type RichTextColor,
  type RichTextStyle,
  type RichTextStyleKey,
} from '../model/richText';
import { simulationTestPayload, validateSimulationTestContext } from '../model/simulationTestContext';
import { containerGeometry } from '../model/containerGeometry';
import { conditionSlots, hasPredicateRack, nextConditionSlotId } from '../model/conditionRack';
import { state, world } from '../state/appState';
import {
  blockMetrics,
  blockSize,
  cloneGraph,
  connectedGraphEdges,
  containerBodyDropZone,
  containerBodyEntryAnchor,
  containerDescendantNodeIds,
  downstreamNodeIds,
  fallbackPosition,
  normalizeConditionBranchLayout,
  nodePosition,
} from '../model/graphLayout';
import { activeConditionOutputSlots, conditionOutputMode, conditionOutputModeKey } from '../model/conditionOutputMode';
import { escapeHtml, shortFingerprint, shortTraceId } from '../utils/dom';
import type {
  ApiResponse,
  BlockDrag,
  BlockCatalog,
  BlockKind,
  GraphDocument,
  GraphHistoryEntry,
  GraphNode,
  GraphPosition,
  SlotBlock,
} from '../model/graphTypes';
import {
  nodeCategoryLabel,
  nodeSummary,
} from './humanize/labels';
import { autoSaveDelayMs, connectedOverlap, doubleClickMs, dragThreshold, historyLimit, normalBlockHeight, normalBlockWidth } from './canvas/blockConstants';
import { renderBlock, renderSlotJoin } from './canvas/blockView';
import { buildBlocks, buildJoins, updateWorldSize, visualBlockById } from './canvas/slotFlowViewModel';
import {
  connectedActionText,
  computeDragDrop,
  findInsertCandidate,
} from './canvas/dragInsert';
import { renderCatalogLibrary } from './catalog/catalogLibrary';
import { updateBlockOverflowMotion } from './canvas/cardOverflow';
import {
  catalogDragGhostRect,
  clearCatalogDragGhost,
  showCatalogContainerTarget,
  showCatalogConditionSlotTarget,
  showCatalogDragGhost,
  updateCatalogDragGhost,
} from './canvas/dragGhostView';
import { catalogConditionSlotAtPoint, placeCatalogNodeInConditionSlot } from './canvas/conditionRackPlacement';
import {
  type BlockRectSnapshot,
  cancelInteractionAnimations,
  captureBlockRects,
  clearPlacementPreview,
  playGraphTransition,
  showPlacementPreview,
} from './canvas/interactionAnimations';
import { renderEditorModal } from './editor/blockEditorModal';
import { bindCustomSelectControls } from './editor/customDropdown';
import {
  insertTextAtRichTextSelection,
  renderRichTextContent,
  richTextSegmentsFromEditor,
  richTextSelectionOffsets,
  setRichTextSelectionOffsets,
} from './editor/richText/richTextEditor';
import { renderSimulationTestContextModal, renderSimulationTestResultSummary, renderTestRunControl } from './simulation/simulationTestContextPanel';
import {
  bindSimulationDraftFields,
  discardSimulationEditorDraft,
  hasSimulationDraftChanges,
  hideSimulationUnsavedConfirm,
  openSimulationEditor,
  requestCloseSimulationEditor,
  saveSimulationEditorDraft,
} from './simulation/simulationContextHandlers';
import { renderTrace } from './trace/traceView';
import { draftStatusText, uncommittedNotice, validationErrorText, validationList, validationSummaryText, validationTitle } from './validation/validationView';
import { renderNodeInfo } from './sidebar/selectionSummary';


const app = document.querySelector<HTMLDivElement>('#app');
const customRichTextColorsKey = 'pixelLogic.richText.customColors';
const customRichTextColorLimit = 10;
type RichTextColorPickMode = 'hue' | 'board';

let scale = 0.86;
let offsetX = 28;
let offsetY = 34;
let isPanning = false;
let panStart = { x: 0, y: 0 };
let panOffset = { x: 0, y: 0 };
let activeBlockDrag: BlockDrag | null = null;
let activeInsertDecorationKey = '';
let lastBlockClick: { nodeId: string; time: number } | null = null;
let graphVersion = 0;
let autoSaveTimer: number | null = null;
let autoSaveInFlight = false;
let autoSaveAgain = false;
let autoSavePromise: Promise<void> | null = null;
let saveSequence = 0;
let confirmedModeSwitchSignature: string | null = null;
const undoStack: GraphHistoryEntry[] = [];
const redoStack: GraphHistoryEntry[] = [];

type RackEditorSession = {
  rootId: string;
  currentNodeId: string;
  draftGraph: GraphDocument;
  originalGraph: GraphDocument;
};

let rackEditorSession: RackEditorSession | null = null;

type ModalScrollSnapshot = {
  editor: number | null;
  simulation: number | null;
};

type TraceScrollSnapshot = {
  top: number;
  stickToBottom: boolean;
};





function currentGraph(): GraphDocument {
  return state.graph ?? fallbackGraph;
}

function captureModalScrollSnapshot(): ModalScrollSnapshot {
  return {
    editor: document.querySelector<HTMLElement>('[data-modal-overlay] .editor-body')?.scrollTop ?? null,
    simulation: document.querySelector<HTMLElement>('[data-sim-modal-overlay] .editor-body')?.scrollTop ?? null,
  };
}

function restoreModalScrollSnapshot(snapshot: ModalScrollSnapshot): void {
  const editorBody = document.querySelector<HTMLElement>('[data-modal-overlay] .editor-body');
  if (snapshot.editor !== null && editorBody) {
    editorBody.scrollTop = snapshot.editor;
  }
  const simulationBody = document.querySelector<HTMLElement>('[data-sim-modal-overlay] .editor-body');
  if (snapshot.simulation !== null && simulationBody) {
    simulationBody.scrollTop = snapshot.simulation;
  }
}

function captureTraceScrollSnapshot(): TraceScrollSnapshot | null {
  const trace = document.querySelector<HTMLElement>('[data-trace-scroll]');
  return trace ? {
    top: trace.scrollTop,
    stickToBottom: trace.scrollHeight - trace.clientHeight - trace.scrollTop <= 8,
  } : null;
}

function restoreTraceScrollSnapshot(snapshot: TraceScrollSnapshot | null): void {
  const trace = document.querySelector<HTMLElement>('[data-trace-scroll]');
  if (snapshot && trace) {
    trace.scrollTop = snapshot.stickToBottom ? trace.scrollHeight : snapshot.top;
  }
}








function renderApp(): void {
  if (!app) {
    return;
  }

  cancelInteractionAnimations();
  clearPlacementPreview(false);
  clearCatalogDragGhost();
  activeInsertDecorationKey = '';

  const editorWasOpen = Boolean(document.querySelector('[data-modal-overlay]'));
  const simulationEditorWasOpen = Boolean(document.querySelector('[data-sim-modal-overlay]'));
  const modalScroll = captureModalScrollSnapshot();
  const traceScroll = captureTraceScrollSnapshot();
  const graph = currentGraph();
  const blocks = buildBlocks(graph, activeCatalog(), state.selectedNodeId);
  const recentNodeId = state.recentNodeId;
  updateWorldSize(blocks, world);
  const joins = buildJoins(graph, blocks);
  const selectedNode = selectedNodeFrom(graph);
  const editorNode = state.editorOpen ? state.editorDraftNode ?? selectedNode : null;
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
          ${renderTestRunControl(state.simulationMenuOpen, apiBusyAttr())}
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
          <div class="panel-title"><span>积木库</span><b>${state.catalogCategoryId ? '具体积木' : '全部分类'}</b></div>
          ${renderCatalogLibrary(activeCatalog(), state.catalogCategoryId)}
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
            <span class="actor-chip">${escapeHtml(state.simulationTestContext.actor.displayName || state.demoActor)}</span>
            <button type="button" data-action="focus">聚焦选中</button>
          </div>
        </div>
        <section class="canvas-viewport" aria-label="可拖动画布">
          <div class="flow-world" style="width:${world.width}px; height:${world.height}px">
            ${joins.map(renderSlotJoin).join('')}
            ${blocks.map((block) => renderBlock(block, recentNodeId)).join('')}
          </div>
          <div class="drag-hint" data-drag-hint aria-live="polite"></div>
        </section>
      </main>

      <aside class="right-panel" aria-label="选中积木信息">
        <div class="panel-title">
          <span>选中积木</span>
          <b>${selectedNode ? escapeHtml(nodeCategoryLabel(selectedNode, activeCatalog())) : '未选中'}</b>
        </div>
        ${selectedNode ? renderNodeInfo(selectedNode, graph, state.selectedNodeId, activeCatalog()) : '<section class="info-card">单击积木选中，拖动积木移动，双击积木编辑。</section>'}
        ${renderSimulationTestResultSummary(state.simulationResult)}
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
        <section data-trace-scroll>
          <div class="panel-title"><span>执行记录</span><b>${state.latestTrace ? escapeHtml(shortTraceId(state.latestTrace.id)) : '无'}</b></div>
          <ol class="trace-list">
            ${renderTrace(state.latestTrace)}
          </ol>
        </section>
      </footer>
      ${state.editorOpen && editorNode ? renderEditorModal(editorNode, activeCatalog(), { editorClosing: state.editorClosing, error: state.error, hasValidation: Boolean(state.validation && !state.validation.valid), modalIssue: state.error || validationSummaryText(state), steady: editorWasOpen, simulationTestContext: state.simulationTestContext, graph: rackEditorSession?.draftGraph ?? graph, rackChildEditing: Boolean(rackEditorSession && rackEditorSession.currentNodeId !== rackEditorSession.rootId) }) : ''}
      ${state.simulationEditorOpen && state.simulationDraftContext ? renderSimulationTestContextModal(state.simulationDraftContext, { closing: state.simulationEditorClosing, error: state.simulationTestContextError, steady: simulationEditorWasOpen }) : ''}
    </section>
  `;

  bindInteractions();
  setTransform();
  updateBlockOverflowMotion();
  restoreModalScrollSnapshot(modalScroll);
  restoreTraceScrollSnapshot(traceScroll);
  focusEditor(editorWasOpen, simulationEditorWasOpen);
  if (recentNodeId) {
    state.recentNodeId = null;
  }
  window.requestAnimationFrame(() => {
    updateBlockOverflowMotion();
  });
}

function activeCatalog(): BlockCatalog {
  return state.catalog ?? fallbackCatalog;
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
  const marginX = 36;
  const marginY = 28;
  const contentWidth = Math.max(1, world.contentWidth);
  const contentHeight = Math.max(1, world.contentHeight);
  scale = Math.min(1, (rect.width - marginX * 2) / contentWidth, (rect.height - marginY * 2) / contentHeight);
  offsetX = marginX - world.minLeft * scale;
  offsetY = marginY - world.minTop * scale;
  setTransform();
}

function centerView(): void {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');

  if (!viewport) {
    return;
  }

  const rect = viewport.getBoundingClientRect();
  scale = 0.78;
  offsetX = rect.width / 2 - ((world.minLeft + world.maxRight) / 2) * scale;
  offsetY = rect.height / 2 - ((world.minTop + world.maxBottom) / 2) * scale;
  setTransform();
}

function focusSelectedBlock(): void {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');
  const selected = visualBlockById(buildBlocks(currentGraph(), activeCatalog(), state.selectedNodeId), state.selectedNodeId);

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
    if (target.closest('button, input, select, textarea')) {
      return;
    }
    const blockEl = target.closest<HTMLElement>('[data-block]');
    if (blockEl?.dataset.block) {
      event.preventDefault();
      beginBlockPointerDown(event, blockEl.dataset.block, viewport);
      return;
    }

    if (target.closest('.slot-join, button, input, select')) {
      return;
    }

    event.preventDefault();
    window.getSelection()?.removeAllRanges();
    isPanning = true;
    panStart = { x: event.clientX, y: event.clientY };
    panOffset = { x: offsetX, y: offsetY };
    document.body.classList.add('is-canvas-panning');
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

    event.preventDefault();
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
    document.body.classList.remove('is-canvas-panning');
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
    document.body.classList.remove('is-canvas-panning');
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
  document.querySelector('[data-sim-menu-toggle]')?.addEventListener('click', () => {
    state.simulationMenuOpen = !state.simulationMenuOpen;
    renderApp();
  });
  document.querySelector('[data-sim-action="open-editor"]')?.addEventListener('click', () => openSimulationEditor(renderApp));
  document.querySelector('.workspace')?.addEventListener('pointerdown', (event) => {
    if (state.simulationMenuOpen && !(event.target as HTMLElement).closest('.test-run-control')) {
      state.simulationMenuOpen = false;
      renderApp();
    }
  });
  document.querySelector('[data-history-action="undo"]')?.addEventListener('click', undoGraphEdit);
  document.querySelector('[data-history-action="redo"]')?.addEventListener('click', redoGraphEdit);
  document.querySelector('[data-graph-action="disconnect-input"]')?.addEventListener('click', disconnectSelectedInput);
  document.querySelector('[data-graph-action="delete-selected"]')?.addEventListener('click', deleteSelectedNode);
  document.querySelectorAll<HTMLButtonElement>('[data-condition-negate]').forEach((buttonEl) => {
    buttonEl.addEventListener('click', (event) => {
      event.stopPropagation();
      const containerId = buttonEl.dataset.conditionNegate;
      const slotId = buttonEl.dataset.conditionSlotId;
      if (containerId && slotId) {
        toggleCanvasConditionNegation(containerId, slotId);
      }
    });
  });
  document.querySelector('[data-modal-action="close"]')?.addEventListener('click', requestCloseEditor);
  document.querySelector('[data-modal-action="cancel"]')?.addEventListener('click', requestCloseEditor);
  document.querySelector('[data-modal-action="save"]')?.addEventListener('click', () => void saveEditorDraft());
  document.querySelector('[data-modal-action="continue-edit"]')?.addEventListener('click', hideUnsavedConfirm);
  document.querySelector('[data-modal-action="discard"]')?.addEventListener('click', discardEditorDraft);
  document.querySelector('[data-modal-action="continue-mode-edit"]')?.addEventListener('click', hideModeSwitchConfirm);
  document.querySelector('[data-modal-action="switch-disconnect"]')?.addEventListener('click', () => {
    confirmedModeSwitchSignature = conditionModeRemovalSignature();
    hideModeSwitchConfirm();
    void saveEditorDraft();
  });
  document.querySelectorAll<HTMLButtonElement>('[data-rack-draft-action]').forEach((buttonEl) => {
    buttonEl.addEventListener('click', () => updateRackEditorDraft(
      buttonEl.dataset.rackDraftAction ?? '',
      buttonEl.dataset.rackSlotId ?? '',
    ));
  });
  document.querySelectorAll<HTMLButtonElement>('[data-rack-edit-node]').forEach((buttonEl) => {
    buttonEl.addEventListener('click', () => {
      if (buttonEl.dataset.rackEditNode) {
        openRackConditionEditor(buttonEl.dataset.rackEditNode);
      }
    });
  });
  document.querySelector('[data-sim-modal-action="close"]')?.addEventListener('click', () => requestCloseSimulationEditor(renderApp));
  document.querySelector('[data-sim-modal-action="cancel"]')?.addEventListener('click', () => requestCloseSimulationEditor(renderApp));
  document.querySelector('[data-sim-modal-action="save"]')?.addEventListener('click', () => saveSimulationEditorDraft(renderApp));
  document.querySelector('[data-sim-modal-action="continue-edit"]')?.addEventListener('click', hideSimulationUnsavedConfirm);
  document.querySelector('[data-sim-modal-action="discard"]')?.addEventListener('click', () => discardSimulationEditorDraft(renderApp));
  bindSimulationDraftFields(renderApp);
  document.querySelectorAll<HTMLButtonElement>('[data-catalog-category]').forEach((buttonEl) => {
    buttonEl.addEventListener('click', () => {
      if (buttonEl.dataset.catalogCategory) {
        state.catalogCategoryId = buttonEl.dataset.catalogCategory;
        renderApp();
      }
    });
  });
  document.querySelector('[data-catalog-back]')?.addEventListener('click', () => {
    state.catalogCategoryId = null;
    renderApp();
  });
  document.querySelectorAll<HTMLButtonElement>('[data-catalog-block]').forEach((buttonEl) => {
    buttonEl.addEventListener('pointerdown', (event) => {
      if (buttonEl.dataset.catalogBlock) {
        beginCatalogPointer(event, buttonEl.dataset.catalogBlock, buttonEl);
      }
    });
  });
  document.querySelector('.editor-overlay')?.addEventListener('pointerdown', (event) => {
    const target = event.target as HTMLElement;
    if (!target.closest('.rich-text-custom-colors') && closeOpenRichTextColorPickers(document)) {
      return;
    }
    if (target.hasAttribute('data-modal-overlay')) {
      requestCloseEditor();
    }
  });
  document.querySelector('[data-sim-modal-overlay]')?.addEventListener('pointerdown', (event) => {
    if ((event.target as HTMLElement).hasAttribute('data-sim-modal-overlay')) {
      requestCloseSimulationEditor(renderApp);
    }
  });

  document.onkeydown = (event) => {
    const editableTarget = isEditableTarget(event.target);
    if (event.key === 'Escape' && activeBlockDrag) {
      event.preventDefault();
      const activeViewport = document.querySelector<HTMLElement>('.canvas-viewport');
      if (activeViewport) {
        cancelBlockDrag(activeViewport);
      }
      return;
    }
    if ((state.editorOpen || state.simulationEditorOpen) && editableTarget && (isUndoShortcut(event) || isRedoShortcut(event))) {
      return;
    }
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
      return;
    }
    if (event.key === 'Escape' && state.simulationEditorOpen) {
      event.preventDefault();
      requestCloseSimulationEditor(renderApp);
    }
    if (event.key === 'Tab' && (state.editorOpen || state.simulationEditorOpen)) {
      trapEditorFocus(event);
    }
  };
  window.onbeforeunload = state.dirty || state.hasDraft || autoSaveInFlight ? () => '还有修改正在自动保存，确定要离开吗？' : null;

  document.querySelectorAll<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>('input[data-node-field], input[data-config-key], select[data-config-key], textarea[data-config-key]').forEach((inputEl) => {
    if (inputEl instanceof HTMLSelectElement) {
      inputEl.addEventListener('change', () => updateEditorDraft(inputEl));
      return;
    }
    inputEl.addEventListener('input', () => updateEditorDraft(inputEl));
  });
  document.querySelectorAll<HTMLElement>('[data-rich-editor="true"]').forEach((editorEl) => {
    editorEl.addEventListener('input', () => {
      updateRichTextEditorDraft(editorEl);
      updateRichTextToolbarState(editorEl.closest('.rich-text-field'));
    });
    editorEl.addEventListener('keydown', (event) => handleRichTextEditorKeydown(event, editorEl));
    editorEl.addEventListener('paste', (event) => handleRichTextEditorPaste(event, editorEl));
    editorEl.addEventListener('keyup', () => updateRichTextToolbarState(editorEl.closest('.rich-text-field')));
    editorEl.addEventListener('mouseup', () => updateRichTextToolbarState(editorEl.closest('.rich-text-field')));
    editorEl.addEventListener('focus', () => updateRichTextToolbarState(editorEl.closest('.rich-text-field')));
  });
  document.onselectionchange = () => updateActiveRichTextToolbarState();
  document.querySelectorAll<HTMLButtonElement>('[data-config-value]').forEach((buttonEl) => {
    buttonEl.addEventListener('click', () => {
      if (buttonEl.dataset.configKey && buttonEl.dataset.configValue) {
        const shouldRerender = editorDraftKeyNeedsRerender(buttonEl.dataset.configKey) || buttonEl.dataset.configValue.startsWith('Y_AT_OR_');
        updateEditorDraftValue(buttonEl.dataset.configKey, buttonEl.dataset.configValue);
        if (shouldRerender) {
          return;
        }
        document.querySelectorAll<HTMLButtonElement>(`[data-config-key="${buttonEl.dataset.configKey}"][data-config-value]`).forEach((item) => {
          item.setAttribute('aria-pressed', String(item === buttonEl));
        });
      }
    });
  });
  bindCustomSelectControls();
  bindRichTextToolbar();

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
  cancelInteractionAnimations();
  clearPlacementPreview(false);
  clearCatalogDragGhost();
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
    joins: buildJoins(graph, buildBlocks(graph, activeCatalog(), state.selectedNodeId)),
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
  });
  const graph = currentGraph();
  drag.candidate = findInsertCandidate(graph, drag, activeCatalog());
  applyDragPreviewPositions(drag);
  renderInsertPreview(drag);
}

function endBlockDrag(event: PointerEvent, viewport: HTMLElement): void {
  const drag = activeBlockDrag;
  if (!drag) {
    return;
  }

  if (drag.started) {
    moveBlockDrag(event);
  }
  activeBlockDrag = null;
  setDragHint('', '');
  viewport.classList.remove('is-block-dragging');
  if (viewport.hasPointerCapture(event.pointerId)) {
    viewport.releasePointerCapture(event.pointerId);
  }

  if (!drag.started) {
    clearInsertPreview();
    selectOrOpenBlock(drag.rootId);
    return;
  }

  const result = computeDragDrop(currentGraph(), drag);
  const { inserted, movedOutOfContainer } = result;
  const actionText = inserted
    ? connectedActionText(drag.candidate)
    : movedOutOfContainer
      ? '已移出容器内部，正在自动保存。'
    : '位置已更新，正在自动保存。';
  applyGraphEdit(result.graph, actionText, { selectedNodeId: drag.rootId, recentNodeId: null });
}

function applyDragPreviewPositions(drag: BlockDrag): void {
  drag.previewPositions.forEach((position, nodeId) => {
    const blockEl = document.querySelector<HTMLElement>(`[data-block="${nodeId}"]`);
    const embeddedParentId = blockEl?.dataset.embeddedParent;
    if (blockEl && (!embeddedParentId || !drag.groupIds.includes(embeddedParentId))) {
      const start = drag.startPositions.get(nodeId);
      if (start) {
        blockEl.style.transform = `translate3d(${position.x - start.x}px, ${position.y - start.y}px, 0)`;
      }
    }
  });
}

function toggleCanvasConditionNegation(containerId: string, slotId: string): void {
  const graph = cloneGraph(currentGraph());
  const container = graph.nodes.find((nodeItem) => nodeItem.id === containerId);
  if (!container || !conditionSlots(container).some((slot) => slot.slotId === slotId)) {
    return;
  }
  container.conditionSlots = conditionSlots(container).map((slot) =>
    slot.slotId === slotId ? { ...slot, negated: !slot.negated } : slot,
  );
  applyGraphEdit(graph, '已切换结束条件取反，正在自动保存。', {
    selectedNodeId: containerId,
    recentNodeId: null,
  });
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
  const drag = activeBlockDrag;
  activeBlockDrag = null;
  clearInsertPreview();
  setDragHint('', '');
  viewport.classList.remove('is-block-dragging');
  if (drag && viewport.hasPointerCapture(drag.pointerId)) {
    viewport.releasePointerCapture(drag.pointerId);
  }
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
  clearPendingAutoSaveTimer();
}

function clearPendingAutoSaveTimer(): void {
  if (autoSaveTimer !== null) {
    window.clearTimeout(autoSaveTimer);
    autoSaveTimer = null;
  }
}

function applyGraphEdit(
  graph: GraphDocument,
  lastAction: string,
  options: {
    selectedNodeId?: string;
    recentNodeId?: string | null;
    refreshOnly?: boolean;
    animationOrigin?: { nodeId: string; rect: BlockRectSnapshot };
  } = {},
): void {
  const animationOrigins = options.animationOrigin
    ? new Map([[options.animationOrigin.nodeId, options.animationOrigin.rect]])
    : new Map<string, BlockRectSnapshot>();
  const firstRects = options.refreshOnly ? null : captureBlockRects(animationOrigins);
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
    if (firstRects) {
      playGraphTransition(firstRects);
    }
  }
}

function restoreGraphHistory(entry: GraphHistoryEntry, lastAction: string): void {
  const firstRects = captureBlockRects();
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
  playGraphTransition(firstRects);
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

function isEditableTarget(target: EventTarget | null): boolean {
  return target instanceof HTMLInputElement
    || target instanceof HTMLTextAreaElement
    || target instanceof HTMLSelectElement
    || (target instanceof HTMLElement && target.isContentEditable);
}



















function renderInsertPreview(drag: BlockDrag): void {
  const candidate = drag.candidate;
  const decorationKey = candidate
    ? `${drag.rootId}:${candidate.kind}:${candidate.join.id}:${candidate.valid ? 'valid' : 'invalid'}`
    : '';
  const decorationChanged = decorationKey !== activeInsertDecorationKey;
  if (decorationChanged) {
    clearInsertDecorations();
    activeInsertDecorationKey = decorationKey;
    if (candidate?.kind === 'insert') {
      const joinEl = document.querySelector<HTMLElement>(`[data-join="${candidate.join.id}"]`);
      joinEl?.classList.add(candidate.valid ? 'insert-target' : 'insert-invalid');
    } else if (candidate?.kind === 'container' && candidate.valid) {
      document.querySelector<HTMLElement>(`[data-block="${candidate.containerNodeId}"]`)?.classList.add('container-target');
    } else if (candidate?.kind === 'condition-slot' && candidate.valid) {
      document.querySelector<HTMLElement>(`[data-condition-container="${candidate.containerNodeId}"][data-condition-slot="${candidate.slotId}"]`)
        ?.classList.add('condition-slot-target');
    }
  }
  if (!candidate) {
    clearPlacementPreview();
    setDragHint('靠近两个积木之间会自动吸附插入。', 'active');
    return;
  }
  if (candidate.valid) {
    const graph = currentGraph();
    const placement = computeDragDrop(graph, drag).graph;
    showPlacementPreview({
      key: `${drag.rootId}:${candidate.kind}:${candidate.join.id}`,
      baseBlocks: buildBlocks(graph, activeCatalog(), state.selectedNodeId),
      placementBlocks: buildBlocks(placement, activeCatalog(), state.selectedNodeId),
      draggedNodeIds: candidate.kind === 'condition-slot' ? new Set([drag.rootId]) : new Set(drag.groupIds),
    });
  } else {
    clearPlacementPreview();
  }
  document.querySelector<HTMLElement>(`[data-block="${candidate.join.from}"]`)?.classList.add('is-related');
  document.querySelector<HTMLElement>(`[data-block="${candidate.join.to}"]`)?.classList.add('is-related');
  setDragHint(candidate.message, candidate.valid ? 'valid' : 'invalid');
}

function clearInsertPreview(): void {
  clearInsertDecorations();
  clearPlacementPreview();
}

function clearInsertDecorations(): void {
  activeInsertDecorationKey = '';
  document.querySelectorAll('.slot-join.insert-target, .slot-join.insert-invalid').forEach((item) => {
    item.classList.remove('insert-target', 'insert-invalid');
  });
  document.querySelectorAll('.logic-block.container-target').forEach((item) => {
    item.classList.remove('container-target');
  });
  document.querySelectorAll('.condition-rack-row.condition-slot-target').forEach((item) => {
    item.classList.remove('condition-slot-target');
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





function addCatalogBlock(blockId: string): void {
  addCatalogBlockAt(blockId, null);
}

function beginCatalogPointer(event: PointerEvent, blockId: string, buttonEl: HTMLElement): void {
  if (event.button !== 0) {
    return;
  }
  event.preventDefault();
  const start = { x: event.clientX, y: event.clientY };
  let moved = false;
  const ghostBlock = catalogGhostBlock(blockId);
  cancelInteractionAnimations();
  clearPlacementPreview(false);
  buttonEl.setPointerCapture(event.pointerId);
  const cleanup = () => {
    buttonEl.removeEventListener('pointermove', onMove);
    buttonEl.removeEventListener('pointerup', onUp);
    buttonEl.removeEventListener('pointercancel', onCancel);
    document.removeEventListener('keydown', onKeyDown);
    if (buttonEl.hasPointerCapture(event.pointerId)) {
      buttonEl.releasePointerCapture(event.pointerId);
    }
  };
  const onMove = (moveEvent: PointerEvent) => {
    if (Math.hypot(moveEvent.clientX - start.x, moveEvent.clientY - start.y) >= dragThreshold) {
      if (!moved && ghostBlock) {
        showCatalogDragGhost(ghostBlock, moveEvent.clientX, moveEvent.clientY, scale);
      }
      moved = true;
    }
    if (!moved) {
      return;
    }
    updateCatalogDragGhost(moveEvent.clientX, moveEvent.clientY);
    const point = pointerToWorld(moveEvent);
    const blockItem = catalogBlock(activeCatalog(), blockId);
    const conditionHit = blockItem
      ? catalogConditionSlotAtPoint(currentGraph(), point, blockItem, activeCatalog())
      : null;
    if (conditionHit) {
      showCatalogConditionSlotTarget(conditionHit.container.id, conditionHit.slotId);
    } else {
      showCatalogContainerTarget(containerAtPoint(currentGraph(), point)?.id ?? null);
    }
  };
  const onUp = (upEvent: PointerEvent) => {
    const ghostRect = catalogDragGhostRect();
    cleanup();
    clearCatalogDragGhost();
    addCatalogBlockAt(blockId, moved ? upEvent : null, ghostRect ? blockRectSnapshot(ghostRect) : null);
  };
  const onCancel = () => {
    cleanup();
    clearCatalogDragGhost();
  };
  const onKeyDown = (keyEvent: KeyboardEvent) => {
    if (keyEvent.key === 'Escape') {
      keyEvent.preventDefault();
      onCancel();
    }
  };
  buttonEl.addEventListener('pointermove', onMove);
  buttonEl.addEventListener('pointerup', onUp);
  buttonEl.addEventListener('pointercancel', onCancel);
  document.addEventListener('keydown', onKeyDown);
}

function addCatalogBlockAt(blockId: string, event: PointerEvent | null, animationOrigin: BlockRectSnapshot | null = null): void {
  const blockItem = catalogBlock(activeCatalog(), blockId);
  if (!blockItem) {
    state.error = '没有找到这个积木，请重新打开积木库。';
    refreshDraftIndicators();
    return;
  }
  const graph = cloneGraph(currentGraph());
  const kind = blockKindFromCatalogBlock(blockItem);
  const nodeItem = createCatalogNode(blockItem, uniqueNodeId(catalogNodeIdPrefix(blockItem), graph), { x: 0, y: 0 });
  const dropPoint = event ? pointerToWorld(event) : null;
  const conditionHit = dropPoint
    ? catalogConditionSlotAtPoint(graph, dropPoint, blockItem, activeCatalog())
    : null;
  const selectedContainer = dropPoint
    ? containerAtPoint(graph, dropPoint)
    : graph.nodes.find((item) => item.id === state.selectedNodeId && item.blockId?.startsWith('control.loop.'));
  if (conditionHit) {
    placeCatalogNodeInConditionSlot(graph, nodeItem, conditionHit);
  } else if (selectedContainer && nodeItem.id !== selectedContainer.id) {
    const childIndex = graph.nodes.filter((item) =>
      item.parentContainerId === selectedContainer.id && (item.parentSlot || 'body') === 'body',
    ).length;
    nodeItem.parentContainerId = selectedContainer.id;
    nodeItem.parentSlot = 'body';
    const size = blockSize(kind);
    const entryAnchor = containerBodyEntryAnchor(graph, selectedContainer);
    const graphWithNode = { ...graph, nodes: [...graph.nodes, nodeItem] };
    const inputY = blockMetrics(graphWithNode, nodeItem).inputY ?? normalBlockHeight / 2;
    const childOrigin = { x: entryAnchor.x, y: entryAnchor.y - inputY };
    const x = dropPoint
      ? dropPoint.x - size.width / 2
      : childOrigin.x + childIndex * (normalBlockWidth - connectedOverlap + containerGeometry.childGap);
    const y = dropPoint ? dropPoint.y - normalBlockHeight / 2 : childOrigin.y;
    nodeItem.position = { x: Math.round(Math.max(childOrigin.x, x)), y: Math.round(Math.max(childOrigin.y, y)) };
  } else {
    nodeItem.position = dropPoint
      ? { x: Math.max(28, Math.round(dropPoint.x - blockSize(kind).width / 2)), y: Math.max(28, Math.round(dropPoint.y - normalBlockHeight / 2)) }
      : visibleDropPosition(kind, nodeItem);
  }
  graph.nodes.push(nodeItem);
  applyGraphEdit(graph, `已新增“${blockItem.displayName}”，正在自动保存。`, {
    selectedNodeId: nodeItem.id,
    recentNodeId: nodeItem.id,
    animationOrigin: animationOrigin ? { nodeId: nodeItem.id, rect: animationOrigin } : undefined,
  });
}

function catalogGhostBlock(blockId: string): SlotBlock | null {
  const blockItem = catalogBlock(activeCatalog(), blockId);
  if (!blockItem) {
    return null;
  }
  const graph = cloneGraph(currentGraph());
  const nodeItem = createCatalogNode(blockItem, uniqueNodeId('catalog-drag-ghost', graph), { x: 0, y: 0 });
  graph.nodes.push(nodeItem);
  return buildBlocks(graph, activeCatalog(), '')
    .find((block) => block.id === nodeItem.id) ?? null;
}

function blockRectSnapshot(rect: DOMRect): BlockRectSnapshot {
  return { left: rect.left, top: rect.top, width: rect.width, height: rect.height };
}

function containerAtPoint(graph: GraphDocument, point: GraphPosition): GraphNode | null {
  return graph.nodes
    .filter((nodeItem) => nodeItem.blockId?.startsWith('control.loop.'))
    .filter((nodeItem) => {
      const rect = containerBodyDropZone(graph, nodeItem);
      return point.x >= rect.x && point.x <= rect.x + rect.width && point.y >= rect.y && point.y <= rect.y + rect.height;
    })
    .sort((left, right) => {
      const leftRect = containerBodyDropZone(graph, left);
      const rightRect = containerBodyDropZone(graph, right);
      return leftRect.width * leftRect.height - rightRect.width * rightRect.height;
    })[0] ?? null;
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

function visibleDropPosition(kind: BlockKind, nodeItem?: GraphNode): GraphPosition {
  const viewport = document.querySelector<HTMLElement>('.canvas-viewport');
  const size = nodeItem && kind === 'condition' && conditionOutputMode(nodeItem) !== 'BRANCH'
    ? { width: normalBlockWidth, height: normalBlockHeight }
    : blockSize(kind);
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
  const deletedIds = new Set([state.selectedNodeId, ...containerDescendantNodeIds(graph, state.selectedNodeId)]);
  if (deletedIds.size > 1 && !window.confirm('此循环块内还有积木，删除后内部积木也会删除。')) {
    return;
  }
  graph.nodes = graph.nodes.filter((nodeItem) => !deletedIds.has(nodeItem.id));
  if (graph.nodes.length === before) {
    return;
  }
  graph.edges = graph.edges.filter((graphEdge) => !deletedIds.has(graphEdge.sourceNodeId) && !deletedIds.has(graphEdge.targetNodeId));
  applyGraphEdit(graph, '已删除积木和相关连接，正在自动保存。', {
    selectedNodeId: graph.nodes[0]?.id ?? '',
    recentNodeId: null,
  });
}

function openEditor(nodeId: string): void {
  const graph = currentGraph();
  const nodeItem = graph.nodes.find((item) => item.id === nodeId);
  if (!nodeItem) {
    return;
  }
  if (hasPredicateRack(nodeItem, activeCatalog())) {
    const draftGraph = cloneGraph(graph);
    rackEditorSession = {
      rootId: nodeId,
      currentNodeId: nodeId,
      draftGraph,
      originalGraph: cloneGraph(graph),
    };
  } else {
    rackEditorSession = null;
  }
  const draftNode = rackEditorSession?.draftGraph.nodes.find((item) => item.id === nodeId) ?? cloneNode(nodeItem);
  state.selectedNodeId = nodeId;
  state.editorDraftNode = draftNode;
  state.editorOriginalNode = cloneNode(nodeItem);
  state.editorSaving = false;
  state.editorOpen = true;
  state.editorClosing = false;
  renderApp();
}

function requestCloseEditor(): void {
  if (rackEditorSession && rackEditorSession.currentNodeId !== rackEditorSession.rootId && !hasCurrentEditorNodeChanges()) {
    returnToRackEditor(false);
    return;
  }
  if (hasEditorDraftChanges()) {
    showUnsavedConfirm();
    return;
  }
  closeEditor();
}

function closeEditor(): void {
  hideUnsavedConfirm();
  hideModeSwitchConfirm();
  confirmedModeSwitchSignature = null;
  state.editorClosing = true;
  const overlayEl = document.querySelector<HTMLElement>('.editor-overlay');
  if (overlayEl) {
    overlayEl.classList.add('is-closing');
  }
  window.setTimeout(() => {
    state.editorOpen = false;
    state.editorClosing = false;
    state.editorDraftNode = null;
    state.editorOriginalNode = null;
    state.editorSaving = false;
    rackEditorSession = null;
    renderApp();
  }, 160);
}

function showUnsavedConfirm(): void {
  const confirmEl = document.querySelector<HTMLElement>('[data-unsaved-confirm]');
  confirmEl?.removeAttribute('hidden');
  document.querySelector<HTMLElement>('[data-modal-action="continue-edit"]')?.focus();
}

function hideUnsavedConfirm(): void {
  document.querySelector<HTMLElement>('[data-unsaved-confirm]')?.setAttribute('hidden', '');
}

function showModeSwitchConfirm(): void {
  const confirmEl = document.querySelector<HTMLElement>('[data-mode-switch-confirm]');
  confirmEl?.removeAttribute('hidden');
  document.querySelector<HTMLElement>('[data-modal-action="continue-mode-edit"]')?.focus();
}

function hideModeSwitchConfirm(): void {
  document.querySelector<HTMLElement>('[data-mode-switch-confirm]')?.setAttribute('hidden', '');
}

function discardEditorDraft(): void {
  if (rackEditorSession && rackEditorSession.currentNodeId !== rackEditorSession.rootId) {
    returnToRackEditor(true);
    return;
  }
  state.editorDraftNode = state.editorOriginalNode ? cloneNode(state.editorOriginalNode) : null;
  closeEditor();
}

function updateRackEditorDraft(action: string, slotId: string): void {
  const session = rackEditorSession;
  const root = session?.draftGraph.nodes.find((nodeItem) => nodeItem.id === session.rootId);
  if (!session || !root || session.currentNodeId !== session.rootId) {
    return;
  }
  if (action === 'add') {
    root.conditionSlots = [...conditionSlots(root), { slotId: nextConditionSlotId(root), negated: false }];
  } else if (action === 'toggle') {
    root.conditionSlots = conditionSlots(root).map((slot) =>
      slot.slotId === slotId ? { ...slot, negated: !slot.negated } : slot,
    );
  } else if (action === 'delete') {
    const member = session.draftGraph.nodes.find((nodeItem) =>
      nodeItem.parentContainerId === root.id && nodeItem.parentSlot === slotId,
    );
    if (member && !window.confirm('删除该条件槽会在保存时同时删除其中的条件积木。确定继续吗？')) {
      return;
    }
    root.conditionSlots = conditionSlots(root).filter((slot) => slot.slotId !== slotId);
    if (member) {
      session.draftGraph.nodes = session.draftGraph.nodes.filter((nodeItem) => nodeItem.id !== member.id);
      session.draftGraph.edges = session.draftGraph.edges.filter((graphEdge) =>
        graphEdge.sourceNodeId !== member.id && graphEdge.targetNodeId !== member.id,
      );
    }
  } else {
    return;
  }
  state.editorDraftNode = root;
  state.error = '';
  hideUnsavedConfirm();
  renderApp();
}

function openRackConditionEditor(nodeId: string): void {
  const session = rackEditorSession;
  const nodeItem = session?.draftGraph.nodes.find((item) => item.id === nodeId);
  if (!session || !nodeItem) {
    return;
  }
  session.currentNodeId = nodeId;
  state.editorDraftNode = nodeItem;
  state.editorOriginalNode = cloneNode(nodeItem);
  state.selectedNodeId = nodeId;
  state.error = '';
  renderApp();
}

function returnToRackEditor(discardChild: boolean): void {
  const session = rackEditorSession;
  if (!session) {
    return;
  }
  if (discardChild && state.editorOriginalNode && session.currentNodeId !== session.rootId) {
    const index = session.draftGraph.nodes.findIndex((nodeItem) => nodeItem.id === session.currentNodeId);
    if (index >= 0) {
      session.draftGraph.nodes[index] = cloneNode(state.editorOriginalNode);
    }
  }
  session.currentNodeId = session.rootId;
  const root = session.draftGraph.nodes.find((nodeItem) => nodeItem.id === session.rootId);
  const originalRoot = session.originalGraph.nodes.find((nodeItem) => nodeItem.id === session.rootId);
  state.editorDraftNode = root ?? null;
  state.editorOriginalNode = originalRoot ? cloneNode(originalRoot) : null;
  state.selectedNodeId = session.rootId;
  confirmedModeSwitchSignature = null;
  hideUnsavedConfirm();
  hideModeSwitchConfirm();
  renderApp();
}

function focusEditor(editorWasOpen = false, simulationEditorWasOpen = false): void {
  if (!state.editorOpen && !state.simulationEditorOpen) {
    return;
  }
  if (state.editorOpen && editorWasOpen) {
    return;
  }
  if (state.simulationEditorOpen && simulationEditorWasOpen) {
    return;
  }
  window.setTimeout(() => {
    const target = document.querySelector<HTMLElement>('.editor-dialog input, .editor-dialog textarea, .editor-dialog select, .editor-dialog [data-rich-editor="true"], #block-editor-title, #simulation-editor-title');
    target?.focus();
  }, 0);
}

function trapEditorFocus(event: KeyboardEvent): void {
  const focusSelector = '.editor-dialog button:not([disabled]), .editor-dialog input:not([disabled]), .editor-dialog textarea:not([disabled]), .editor-dialog select:not([disabled]), .editor-dialog [data-rich-editor="true"], #block-editor-title';
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

function updateEditorDraft(inputEl: HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement): void {
  if (inputEl.dataset.nodeField === 'displayName') {
    updateEditorDraftValue('displayName', inputEl.value, 'node');
  }
  if (inputEl.dataset.configKey) {
    updateEditorDraftValue(inputEl.dataset.configKey, inputEl.value);
  }
}

function bindRichTextToolbar(): void {
  document.querySelectorAll<HTMLButtonElement>('[data-rich-style]').forEach((buttonEl) => {
    buttonEl.addEventListener('pointerdown', (event) => event.preventDefault());
    buttonEl.addEventListener('click', () => {
      const key = buttonEl.dataset.richStyle as RichTextStyleKey | undefined;
      if (key) {
        applyRichTextToolbarStyle(buttonEl, key);
      }
    });
  });
  document.querySelectorAll<HTMLButtonElement>('[data-rich-color]').forEach((buttonEl) => {
    buttonEl.addEventListener('pointerdown', (event) => event.preventDefault());
    buttonEl.addEventListener('click', () => applyRichTextToolbarColor(buttonEl));
  });
  document.querySelectorAll<HTMLButtonElement>('[data-rich-custom-color-open]').forEach((buttonEl) => {
    buttonEl.addEventListener('pointerdown', (event) => event.preventDefault());
    buttonEl.addEventListener('click', () => toggleRichTextColorPicker(buttonEl));
  });
  document.querySelectorAll<HTMLElement>('[data-rich-color-ring]').forEach((ringEl) => {
    bindRichTextColorPickerSurface(ringEl, 'hue');
  });
  document.querySelectorAll<HTMLElement>('[data-rich-color-board]').forEach((boardEl) => {
    bindRichTextColorPickerSurface(boardEl, 'board');
  });
  document.querySelectorAll<HTMLButtonElement>('[data-rich-color-apply]').forEach((buttonEl) => {
    buttonEl.addEventListener('pointerdown', (event) => event.preventDefault());
    buttonEl.addEventListener('click', () => applyRichTextCustomColor(buttonEl));
  });
  document.querySelectorAll<HTMLButtonElement>('[data-rich-color-close]').forEach((buttonEl) => {
    buttonEl.addEventListener('pointerdown', (event) => event.preventDefault());
    buttonEl.addEventListener('click', () => closeRichTextColorPicker(buttonEl.closest('.rich-text-custom-colors')));
  });
  bindRichTextRecentColorButtons();
  hydrateRichTextRecentColors();
}

function updateRichTextEditorDraft(editorEl: HTMLElement): void {
  const key = editorEl.dataset.configKey;
  if (!key || !state.editorDraftNode) {
    return;
  }
  const segments = editorEl.textContent ? richTextSegmentsFromEditor(editorEl) : [];
  updateEditorDraftValue(key, serializeRichText({ segments }));
}

function handleRichTextEditorKeydown(event: KeyboardEvent, editorEl: HTMLElement): void {
  if (event.key !== 'Enter') {
    return;
  }
  event.preventDefault();
  if (insertTextAtRichTextSelection(editorEl, '\n')) {
    updateRichTextEditorDraft(editorEl);
    updateRichTextToolbarState(editorEl.closest('.rich-text-field'));
  }
}

function handleRichTextEditorPaste(event: ClipboardEvent, editorEl: HTMLElement): void {
  event.preventDefault();
  const text = event.clipboardData?.getData('text/plain') ?? '';
  if (insertTextAtRichTextSelection(editorEl, text)) {
    updateRichTextEditorDraft(editorEl);
    updateRichTextToolbarState(editorEl.closest('.rich-text-field'));
  }
}

function applyRichTextToolbarStyle(controlEl: HTMLElement, key: RichTextStyleKey): void {
  const context = richTextToolbarContext(controlEl);
  if (!context) {
    return;
  }
  const selectionState = richTextSelectionState(context.raw, context.start, context.end);
  applyRichTextToolbarPatch(context, { [key]: !selectionState.styles[key] } as Partial<RichTextStyle>);
}

function applyRichTextToolbarColor(controlEl: HTMLElement): void {
  const context = richTextToolbarContext(controlEl);
  if (!context) {
    return;
  }
  const selectedColor = normalizeRichTextColor(controlEl.dataset.richColor ?? '') ?? '';
  const selectionState = richTextSelectionState(context.raw, context.start, context.end);
  const nextColor = selectionState.color === selectedColor ? undefined : selectedColor || undefined;
  applyRichTextToolbarPatch(context, { color: nextColor });
}

function bindRichTextRecentColorButtons(): void {
  document.querySelectorAll<HTMLButtonElement>('[data-rich-recent-color]').forEach((buttonEl) => {
    buttonEl.addEventListener('pointerdown', (event) => event.preventDefault());
    buttonEl.addEventListener('click', () => {
      if (buttonEl.dataset.richColor) {
        applyRichTextToolbarColor(buttonEl);
      }
    });
  });
}

function toggleRichTextColorPicker(controlEl: HTMLElement): void {
  const picker = controlEl.closest('.rich-text-custom-colors')?.querySelector<HTMLElement>('[data-rich-color-picker]');
  if (!picker) {
    return;
  }
  delete picker.dataset.richConfigKey;
  delete picker.dataset.richSelectionStart;
  delete picker.dataset.richSelectionEnd;
  captureRichTextPickerContext(controlEl, picker);
  picker.hidden = !picker.hidden;
  updateRichTextColorPickerPreview(picker);
}

function closeRichTextColorPicker(root: Element | null): void {
  const picker = root?.querySelector<HTMLElement>('[data-rich-color-picker]');
  if (picker) {
    picker.hidden = true;
  }
}

function closeOpenRichTextColorPickers(root: ParentNode): boolean {
  const pickers = Array.from(root.querySelectorAll<HTMLElement>('[data-rich-color-picker]:not([hidden])'));
  pickers.forEach((picker) => {
    picker.hidden = true;
  });
  return pickers.length > 0;
}

function bindRichTextColorPickerSurface(surfaceEl: HTMLElement, mode: RichTextColorPickMode): void {
  surfaceEl.addEventListener('pointerdown', (event) => {
    event.preventDefault();
    surfaceEl.setPointerCapture(event.pointerId);
    updateRichTextColorPickerFromPointer(event, surfaceEl, mode);
  });
  surfaceEl.addEventListener('pointermove', (event) => {
    if (surfaceEl.hasPointerCapture(event.pointerId)) {
      updateRichTextColorPickerFromPointer(event, surfaceEl, mode);
    }
  });
  surfaceEl.addEventListener('pointerup', (event) => {
    if (surfaceEl.hasPointerCapture(event.pointerId)) {
      surfaceEl.releasePointerCapture(event.pointerId);
    }
  });
}

function updateRichTextColorPickerFromPointer(event: PointerEvent, surfaceEl: HTMLElement, mode: RichTextColorPickMode): void {
  const picker = surfaceEl.closest<HTMLElement>('[data-rich-color-picker]');
  if (!picker) {
    return;
  }
  const rect = surfaceEl.getBoundingClientRect();
  if (mode === 'hue') {
    const dx = event.clientX - rect.left - rect.width / 2;
    const dy = event.clientY - rect.top - rect.height / 2;
    picker.dataset.richHue = String(Math.round((Math.atan2(dy, dx) * 180 / Math.PI + 360) % 360));
    if (Number(picker.dataset.richValue) < 20) {
      picker.dataset.richValue = '100';
    }
    if (Number(picker.dataset.richSaturation) < 20) {
      picker.dataset.richSaturation = '100';
    }
  } else {
    const x = Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width));
    const y = Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height));
    picker.dataset.richSaturation = String(Math.round(x * 100));
    picker.dataset.richValue = String(Math.round((1 - y) * 100));
  }
  updateRichTextColorPickerPreview(picker);
}

function updateRichTextColorPickerPreview(picker: HTMLElement | null): void {
  if (!picker) {
    return;
  }
  const hue = clampNumber(Number(picker.dataset.richHue), 0, 360, 120);
  const saturation = clampNumber(Number(picker.dataset.richSaturation), 0, 100, 67);
  const value = clampNumber(Number(picker.dataset.richValue), 0, 100, 100);
  const color = hsvToHex(hue, saturation, value);
  picker.dataset.richColor = color;
  picker.style.setProperty('--picked-hue', String(hue));
  picker.style.setProperty('--picked-saturation', `${saturation}%`);
  picker.style.setProperty('--picked-value', `${value}%`);
  picker.style.setProperty('--picked-color', color);
  const valueEl = picker.querySelector<HTMLElement>('[data-rich-color-value]');
  if (valueEl) {
    valueEl.textContent = color;
  }
}

function applyRichTextCustomColor(controlEl: HTMLElement): void {
  const root = controlEl.closest<HTMLElement>('.rich-text-custom-colors');
  const picker = root?.querySelector<HTMLElement>('[data-rich-color-picker]');
  const displayedColor = picker?.querySelector<HTMLElement>('[data-rich-color-value]')?.textContent?.trim() ?? '';
  const color = normalizeRichTextColor(displayedColor || picker?.dataset.richColor || '');
  if (!color) {
    return;
  }
  const context = richTextPickerContext(controlEl);
  rememberRichTextCustomColor(color);
  hydrateRichTextRecentColors();
  closeRichTextColorPicker(root);
  if (!context) {
    return;
  }
  controlEl.dataset.richColor = color;
  applyRichTextToolbarColor(controlEl);
  delete controlEl.dataset.richColor;
}

function captureRichTextPickerContext(controlEl: HTMLElement, picker: HTMLElement): void {
  const context = richTextToolbarContext(controlEl);
  if (!context) {
    return;
  }
  picker.dataset.richConfigKey = context.key;
  picker.dataset.richSelectionStart = String(context.start);
  picker.dataset.richSelectionEnd = String(context.end);
  storeRichTextSelection(context.fieldEl, context);
}

function richTextPickerContext(controlEl: HTMLElement): {
  fieldEl: HTMLElement;
  editorEl: HTMLElement;
  key: string;
  raw: string;
  start: number;
  end: number;
} | null {
  const fieldEl = controlEl.closest<HTMLElement>('.rich-text-field');
  const editorEl = fieldEl?.querySelector<HTMLElement>('[data-rich-editor="true"]');
  const picker = controlEl.closest<HTMLElement>('[data-rich-color-picker]');
  const key = picker?.dataset.richConfigKey ?? editorEl?.dataset.configKey;
  const start = Number(picker?.dataset.richSelectionStart);
  const end = Number(picker?.dataset.richSelectionEnd);
  const pickerSelection = Number.isFinite(start) && Number.isFinite(end) && start !== end
    ? { start: Math.min(start, end), end: Math.max(start, end) }
    : null;
  const fallbackSelection = fieldEl ? richTextStoredSelection(fieldEl) : null;
  const activeSelection = pickerSelection ?? fallbackSelection;
  if (!fieldEl || !editorEl || !key || !state.editorDraftNode || !activeSelection) {
    return null;
  }
  return {
    fieldEl,
    editorEl,
    key,
    raw: state.editorDraftNode.config[key] ?? '',
    start: activeSelection.start,
    end: activeSelection.end,
  };
}

function rememberRichTextCustomColor(color: RichTextColor): void {
  if (!String(color).startsWith('#')) {
    return;
  }
  const colors = [color, ...readRichTextCustomColors().filter((item) => item !== color)].slice(0, customRichTextColorLimit);
  try {
    window.localStorage.setItem(customRichTextColorsKey, JSON.stringify(colors));
  } catch {
    // localStorage may be blocked; the current apply still works.
  }
}

function readRichTextCustomColors(): RichTextColor[] {
  try {
    const parsed = JSON.parse(window.localStorage.getItem(customRichTextColorsKey) ?? '[]') as string[];
    return parsed.map((item) => normalizeRichTextColor(item)).filter((item): item is RichTextColor => Boolean(item && String(item).startsWith('#')));
  } catch {
    return [];
  }
}

function hydrateRichTextRecentColors(): void {
  const colors = readRichTextCustomColors();
  document.querySelectorAll<HTMLButtonElement>('[data-rich-recent-color]').forEach((buttonEl) => {
    const index = Number(buttonEl.dataset.richRecentColor ?? '0');
    const color = colors[index];
    if (color) {
      buttonEl.dataset.richColor = color;
      buttonEl.style.setProperty('--recent-color', color);
      buttonEl.title = color;
      buttonEl.setAttribute('aria-label', `最近自定义颜色 ${color}`);
      return;
    }
    delete buttonEl.dataset.richColor;
    buttonEl.style.removeProperty('--recent-color');
    buttonEl.title = '空自定义颜色';
    buttonEl.setAttribute('aria-label', '空自定义颜色');
  });
}

function clampNumber(value: number, min: number, max: number, fallback: number): number {
  if (!Number.isFinite(value)) {
    return fallback;
  }
  return Math.min(max, Math.max(min, value));
}

function hsvToHex(hue: number, saturation: number, value: number): `#${string}` {
  const h = ((hue % 360) + 360) % 360 / 60;
  const s = saturation / 100;
  const v = value / 100;
  const c = v * s;
  const x = c * (1 - Math.abs(h % 2 - 1));
  const m = v - c;
  const [r, g, b] = h < 1 ? [c, x, 0]
    : h < 2 ? [x, c, 0]
      : h < 3 ? [0, c, x]
        : h < 4 ? [0, x, c]
          : h < 5 ? [x, 0, c]
            : [c, 0, x];
  return `#${[r, g, b].map((channel) => Math.round((channel + m) * 255).toString(16).padStart(2, '0')).join('')}`;
}

function richTextToolbarContext(controlEl: HTMLElement): {
  fieldEl: HTMLElement;
  editorEl: HTMLElement;
  key: string;
  raw: string;
  start: number;
  end: number;
} | null {
  const fieldEl = controlEl.closest<HTMLElement>('.rich-text-field');
  const editorEl = fieldEl?.querySelector<HTMLElement>('[data-rich-editor="true"]');
  const key = editorEl?.dataset.configKey;
  if (!fieldEl || !editorEl || !key || !state.editorDraftNode) {
    return null;
  }
  const selection = richTextSelectionOffsets(editorEl);
  const storedSelection = richTextStoredSelection(fieldEl);
  if (selection && selection.start !== selection.end) {
    storeRichTextSelection(fieldEl, selection);
  }
  const activeSelection = selection && selection.start !== selection.end ? selection : storedSelection;
  if (!activeSelection || activeSelection.start === activeSelection.end) {
    editorEl.focus();
    return null;
  }
  return {
    fieldEl,
    editorEl,
    key,
    raw: state.editorDraftNode.config[key] ?? '',
    start: activeSelection.start,
    end: activeSelection.end,
  };
}

function richTextStoredSelection(fieldEl: HTMLElement): { start: number; end: number } | null {
  const start = Number(fieldEl.dataset.richSelectionStart);
  const end = Number(fieldEl.dataset.richSelectionEnd);
  if (Number.isFinite(start) && Number.isFinite(end) && start !== end) {
    return { start: Math.min(start, end), end: Math.max(start, end) };
  }
  return null;
}

function storeRichTextSelection(fieldEl: HTMLElement, selection: { start: number; end: number }): void {
  fieldEl.dataset.richSelectionStart = String(selection.start);
  fieldEl.dataset.richSelectionEnd = String(selection.end);
}

function applyRichTextToolbarPatch(
  context: { fieldEl: HTMLElement; editorEl: HTMLElement; key: string; raw: string; start: number; end: number },
  patch: Partial<RichTextStyle>,
): void {
  const currentRaw = serializeRichText({ segments: richTextSegmentsFromEditor(context.editorEl) });
  const next = applyRichTextStyle(currentRaw, context.start, context.end, patch);
  context.editorEl.innerHTML = renderRichTextContent(next);
  updateEditorDraftValue(context.key, next);
  setRichTextSelectionOffsets(context.editorEl, context.start, context.end);
  updateRichTextToolbarState(context.fieldEl);
}

function updateActiveRichTextToolbarState(): void {
  const editorEl = activeRichTextEditor();
  if (!editorEl) {
    resetRichTextToolbarState(document);
    return;
  }
  updateRichTextToolbarState(editorEl.closest('.rich-text-field'));
}

function updateRichTextToolbarState(root: Element | null): void {
  if (!root) {
    resetRichTextToolbarState(document);
    return;
  }
  resetRichTextToolbarState(root);
  const editorEl = root.querySelector<HTMLElement>('[data-rich-editor="true"]');
  const key = editorEl?.dataset.configKey;
  if (!editorEl || !key || !state.editorDraftNode) {
    return;
  }
  const selection = richTextSelectionOffsets(editorEl);
  if (!selection || selection.start === selection.end) {
    return;
  }
  storeRichTextSelection(root as HTMLElement, selection);
  const selectionState = richTextSelectionState(state.editorDraftNode.config[key] ?? '', selection.start, selection.end);
  root.querySelectorAll<HTMLButtonElement>('[data-rich-style]').forEach((buttonEl) => {
    const keyName = buttonEl.dataset.richStyle as RichTextStyleKey | undefined;
    buttonEl.setAttribute('aria-pressed', String(Boolean(keyName && selectionState.styles[keyName])));
  });
  root.querySelectorAll<HTMLButtonElement>('[data-rich-color]').forEach((buttonEl) => {
    const color = buttonEl.dataset.richColor ?? '';
    buttonEl.setAttribute('aria-pressed', String(selectionState.color !== undefined && color === selectionState.color));
  });
}

function resetRichTextToolbarState(root: ParentNode): void {
  root.querySelectorAll<HTMLButtonElement>('[data-rich-style], [data-rich-color]').forEach((buttonEl) => {
    buttonEl.setAttribute('aria-pressed', 'false');
  });
}

function activeRichTextEditor(): HTMLElement | null {
  const selection = window.getSelection();
  const node = selection?.anchorNode;
  const element = node instanceof HTMLElement ? node : node?.parentElement;
  return element?.closest<HTMLElement>('[data-rich-editor="true"]') ?? null;
}

function updateEditorDraftValue(key: string, value: string, target: 'config' | 'node' = 'config'): void {
  const draftNode = state.editorDraftNode;
  if (!draftNode) {
    return;
  }

  if (target === 'node' && key === 'displayName') {
    if (draftNode.displayName === value) {
      return;
    }
    draftNode.displayName = value;
  } else {
    if (key === conditionOutputModeKey && applyYCompareOutputChoice(draftNode, value)) {
      state.error = '';
      hideUnsavedConfirm();
      confirmedModeSwitchSignature = null;
      hideModeSwitchConfirm();
      renderApp();
      return;
    }
    if (draftNode.config[key] === value) {
      return;
    }
    draftNode.config[key] = value;
    if (key === 'valueType') {
      if (value === 'BOOLEAN' && !['true', 'false'].includes(draftNode.config.value ?? '')) {
        draftNode.config.value = 'true';
      }
      if (value === 'INTEGER' && ['true', 'false', ''].includes(draftNode.config.value ?? '')) {
        draftNode.config.value = '1';
      }
      if (value === 'STRING' && ['true', 'false'].includes(draftNode.config.value ?? '')) {
        draftNode.config.value = '';
      }
    }
  }
  state.error = '';
  hideUnsavedConfirm();
  if (key === conditionOutputModeKey) {
    confirmedModeSwitchSignature = null;
    hideModeSwitchConfirm();
  }
  if (editorDraftKeyNeedsRerender(key)) {
    renderApp();
    return;
  }
  refreshEditorDraftIndicators();
}

function editorDraftKeyNeedsRerender(key: string): boolean {
  return key === 'valueType' || key === 'compareMode';
}

function applyYCompareOutputChoice(nodeItem: GraphNode, value: string): boolean {
  if (!isYCompareNode(nodeItem)) {
    return false;
  }
  if (value === 'Y_AT_OR_ABOVE') {
    nodeItem.config.compareMode = 'AT_OR_ABOVE';
    nodeItem.config[conditionOutputModeKey] = 'PASS_ONLY';
    return true;
  }
  if (value === 'Y_AT_OR_BELOW') {
    nodeItem.config.compareMode = 'AT_OR_BELOW';
    nodeItem.config[conditionOutputModeKey] = 'PASS_ONLY';
    return true;
  }
  return false;
}

function isYCompareNode(nodeItem: GraphNode): boolean {
  return nodeItem.blockId === 'condition.player.y_compare'
    || nodeItem.blockId === 'condition.target_block.y_compare'
    || nodeItem.type === 'PLAYER_Y_COMPARE_CONDITION'
    || nodeItem.type === 'TARGET_BLOCK_Y_COMPARE_CONDITION';
}

async function saveEditorDraft(): Promise<void> {
  if (state.editorSaving || !state.editorDraftNode || !state.editorOriginalNode) {
    return;
  }
  if (rackEditorSession && rackEditorSession.currentNodeId !== rackEditorSession.rootId) {
    if (!hasCurrentEditorNodeChanges()) {
      returnToRackEditor(false);
      return;
    }
    const childRemovalSignature = conditionModeRemovalSignature();
    if (childRemovalSignature && childRemovalSignature !== confirmedModeSwitchSignature) {
      showModeSwitchConfirm();
      return;
    }
    const child = state.editorDraftNode;
    rackEditorSession.draftGraph.edges = rackEditorSession.draftGraph.edges.filter((graphEdge) =>
      graphEdge.sourceNodeId !== child.id || activeConditionOutputSlots(child).includes(graphEdge.sourceSlotId),
    );
    returnToRackEditor(false);
    return;
  }
  if (!hasEditorDraftChanges()) {
    closeEditor();
    return;
  }

  const graph = currentGraph();
  const removalSignature = conditionModeRemovalSignature();
  if (removalSignature && removalSignature !== confirmedModeSwitchSignature) {
    showModeSwitchConfirm();
    return;
  }

  const nextGraph = rackEditorSession
    ? cloneGraph(rackEditorSession.draftGraph)
    : cloneGraph(graph);
  const nextNode = nextGraph.nodes.find((item) => item.id === state.editorOriginalNode?.id);
  if (!nextNode) {
    state.error = '保存失败：当前积木不存在。';
    renderApp();
    return;
  }

  nextNode.displayName = state.editorDraftNode.displayName;
  nextNode.config = { ...state.editorDraftNode.config };
  nextNode.conditionSlots = conditionSlots(state.editorDraftNode).map((slot) => ({ ...slot }));
  nextGraph.edges = nextGraph.edges.filter((graphEdge) =>
    graphEdge.sourceNodeId !== nextNode.id || activeConditionOutputSlots(nextNode).includes(graphEdge.sourceSlotId),
  );
  state.editorOriginalNode = cloneNode(state.editorDraftNode);
  state.editorSaving = true;
  confirmedModeSwitchSignature = null;
  applyGraphEdit(nextGraph, '内容已修改，正在保存。', { selectedNodeId: nextNode.id, refreshOnly: true });
  clearPendingAutoSaveTimer();

  try {
    const saved = await saveAndCommit({ version: graphVersion });
    state.apiStatus = 'online';
    state.statusMessage = 'API 已连接';
    if (saved) {
      closeEditor();
    } else {
      state.editorSaving = false;
      renderApp();
    }
  } catch (error) {
    const connected = error instanceof PixelLogicApiError ? error.connected : false;
    state.apiStatus = connected ? 'online' : 'offline';
    state.statusMessage = connected ? 'API 已连接' : 'API 未连接';
    state.error = error instanceof Error ? error.message : 'API 未连接';
    state.lastAction = '保存失败，请确认 API 连接或修复检查问题。';
    state.editorSaving = false;
    renderApp();
  }
}

function refreshEditorDraftIndicators(): void {
  const draftNode = state.editorDraftNode;
  if (!draftNode) {
    return;
  }
  const modalSummary = document.querySelector<HTMLElement>('[data-modal-summary]');
  const modalError = document.querySelector<HTMLElement>('[data-modal-error]');
  if (modalSummary) {
    modalSummary.textContent = nodeSummary(draftNode, activeCatalog());
  }
  if (modalError) {
    modalError.textContent = state.error || validationSummaryText(state);
  }
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
    const selected = state.editorDraftNode ?? selectedNodeFrom(currentGraph());
    modalSummary.textContent = selected ? nodeSummary(selected, activeCatalog()) : '';
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
    if (activeBlockDrag) {
      scheduleAutoSave();
      return;
    }
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
  if (activeBlockDrag) {
    scheduleAutoSave();
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
    if (activeBlockDrag || state.editorOpen) {
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

async function loadCatalog(): Promise<void> {
  await runAction('加载积木库', async () => {
    const catalogResponse = await api('/api/pixellogic/catalog');
    state.catalog = catalogResponse.catalog ?? fallbackCatalog;
    if (state.catalogCategoryId && !state.catalog.categories.some((categoryItem) => categoryItem.id === state.catalogCategoryId)) {
      state.catalogCategoryId = null;
    }
    state.apiStatus = 'online';
    state.statusMessage = '积木库已加载';
  }, { renderBusy: false });
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
    const contextError = validateSimulationTestContext(state.simulationTestContext);
    if (contextError) {
      state.simulationTestContextError = contextError;
      state.error = contextError;
      state.lastAction = '测试上下文需要调整';
      return;
    }
    state.simulationTestContextError = '';
    stopTestRunPolling();
    state.simulationResult = null;
    await waitForPendingAutoSave();
    if (state.dirty || state.hasDraft) {
      const saved = await saveAndCommit({ version: graphVersion });
      if (!saved) {
        return;
      }
    }
    await api('/api/pixellogic/test/reset', { method: 'POST' });
    const data = await api('/api/pixellogic/test/start', {
      method: 'POST',
      body: JSON.stringify(simulationTestPayload(state.simulationTestContext)),
    });
    state.apiStatus = 'online';
    state.latestTrace = data.trace ?? null;
    state.simulationResult = data.simulation ?? null;
    state.lastAction = data.message || '测试运行已执行';
    if (!data.runId || !data.runStatus || typeof data.terminal !== 'boolean') {
      throw new Error('API 未返回测试运行状态。');
    }
    if (!data.terminal) {
      startTestRunPolling(data.runId, {
        onUpdate: (update) => {
          state.apiStatus = 'online';
          state.latestTrace = update.trace ?? state.latestTrace;
          state.simulationResult = update.simulation ?? state.simulationResult;
          state.lastAction = update.message || state.lastAction;
          renderApp();
        },
        onFailure: (error) => {
          const connected = error instanceof PixelLogicApiError ? error.connected : false;
          state.apiStatus = connected ? 'online' : 'offline';
          state.statusMessage = connected ? 'API 已连接' : 'API 未连接';
          state.error = error instanceof Error ? error.message : '测试运行状态查询失败。';
          renderApp();
        },
      });
    }
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
    if (!state.editorClosing && !state.simulationEditorClosing) {
      renderApp();
    }
  }
}












function selectedNodeFrom(graph: GraphDocument): GraphNode | null {
  return graph.nodes.find((nodeItem) => nodeItem.id === state.selectedNodeId) ?? graph.nodes[0] ?? null;
}

function cloneNode(nodeItem: GraphNode): GraphNode {
  return {
    ...nodeItem,
    config: { ...nodeItem.config },
    conditionSlots: conditionSlots(nodeItem).map((slot) => ({ ...slot })),
    position: nodeItem.position ? { ...nodeItem.position } : undefined,
    slots: nodeItem.slots.map((slot) => ({ ...slot })),
  };
}

function hasEditorDraftChanges(): boolean {
  if (rackEditorSession) {
    if (rackEditorSession.currentNodeId !== rackEditorSession.rootId) {
      return hasCurrentEditorNodeChanges();
    }
    return JSON.stringify(rackEditorSession.draftGraph) !== JSON.stringify(rackEditorSession.originalGraph);
  }
  return Boolean(state.editorDraftNode && state.editorOriginalNode)
    && JSON.stringify(state.editorDraftNode) !== JSON.stringify(state.editorOriginalNode);
}

function hasCurrentEditorNodeChanges(): boolean {
  return Boolean(state.editorDraftNode && state.editorOriginalNode)
    && JSON.stringify(state.editorDraftNode) !== JSON.stringify(state.editorOriginalNode);
}

function conditionModeRemovalSignature(): string | null {
  const draftNode = state.editorDraftNode;
  const originalNode = state.editorOriginalNode;
  if (!draftNode || !originalNode || !draftNode.type.includes('CONDITION')) {
    return null;
  }
  if (conditionOutputMode(draftNode) === conditionOutputMode(originalNode)) {
    return null;
  }
  const activeSlots = new Set(activeConditionOutputSlots(draftNode));
  const removedEdgeIds = currentGraph().edges
    .filter((graphEdge) => graphEdge.sourceNodeId === originalNode.id && !activeSlots.has(graphEdge.sourceSlotId))
    .map((graphEdge) => graphEdge.id)
    .sort();
  return removedEdgeIds.length > 0 ? `${originalNode.id}:${conditionOutputMode(originalNode)}:${conditionOutputMode(draftNode)}:${removedEdgeIds.join(',')}` : null;
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
  window.addEventListener('pagehide', stopTestRunPolling, { once: true });
  centerView();
  void loadCatalog().then(() => loadGraph()).then(() => {
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
