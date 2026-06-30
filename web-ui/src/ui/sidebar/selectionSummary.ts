import type { BlockCatalog, GraphDocument, GraphEdge, GraphNode } from '../../model/graphTypes';
import { connectedGraphEdges } from '../../model/graphLayout';
import { escapeHtml } from '../../utils/dom';
import { nodeConfigItems } from '../editor/formControls';
import { nodeSummary, slotLabel } from '../humanize/labels';

export function renderNodeInfo(nodeItem: GraphNode, graph: GraphDocument, selectedNodeId: string, catalog: BlockCatalog): string {
  const configItems = nodeConfigItems(nodeItem, catalog);
  return `
    <section class="info-card">
      <b>${escapeHtml(nodeItem.displayName || nodeItem.id)}</b>
      <p>${escapeHtml(nodeSummary(nodeItem, catalog))}</p>
    </section>
    <section class="info-card">
      <b>配置摘要</b>
      ${configItems.length > 0 ? `
        <dl class="config-list">
          ${configItems
            .map((item) => `<div><dt>${escapeHtml(item.label)}</dt><dd>${escapeHtml(item.value)}</dd></div>`)
            .join('')}
        </dl>
      ` : '<p>该积木当前没有额外配置。</p>'}
    </section>
    ${renderConnectionInfo(nodeItem, graph, selectedNodeId)}
    <section class="info-card">
      <b>提示</b>
      <p>单击选中，拖动移动积木和后续链条，双击打开编辑窗口。</p>
    </section>
  `;
}

function renderConnectionInfo(nodeItem: GraphNode, graph: GraphDocument, selectedNodeId: string): string {
  const edges = connectedGraphEdges(graph);
  const incoming = edges.filter((graphEdge) => graphEdge.targetNodeId === nodeItem.id);
  const outgoing = edges.filter((graphEdge) => graphEdge.sourceNodeId === nodeItem.id);
  const canDelete = !Object.values(graph.triggerEntries).includes(nodeItem.id);

  return `
    <section class="info-card connection-card">
      <b>连接</b>
      <dl class="config-list">
        <div><dt>输入</dt><dd>${escapeHtml(incoming.length > 0 ? incoming.map((graphEdge) => edgeSummary(graph, graphEdge)).join('；') : '未连接')}</dd></div>
        <div><dt>输出</dt><dd>${escapeHtml(outgoing.length > 0 ? outgoing.map((graphEdge) => edgeSummary(graph, graphEdge)).join('；') : '未连接')}</dd></div>
      </dl>
      <div class="info-actions">
        <button type="button" class="ghost-button" data-graph-action="disconnect-input" ${incoming.length === 0 ? 'disabled' : ''}>断开输入</button>
        <button type="button" class="ghost-button danger" data-graph-action="delete-selected" ${canDelete ? '' : 'disabled'}>删除积木</button>
      </div>
    </section>
  `;
}

function edgeSummary(graph: GraphDocument, graphEdge: GraphEdge): string {
  const source = graph.nodes.find((nodeItem) => nodeItem.id === graphEdge.sourceNodeId);
  const target = graph.nodes.find((nodeItem) => nodeItem.id === graphEdge.targetNodeId);
  return `${source?.displayName ?? graphEdge.sourceNodeId}.${slotLabel(graphEdge.sourceSlotId)} -> ${target?.displayName ?? graphEdge.targetNodeId}`;
}
