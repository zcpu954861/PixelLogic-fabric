import {
  catalogBlocksForCategory,
  catalogCategories,
  catalogCategory,
} from '../../model/blockCatalog';
import type { BlockCatalog } from '../../model/graphTypes';
import { escapeAttr, escapeHtml } from '../../utils/dom';

export function renderCatalogLibrary(catalog: BlockCatalog, selectedCategoryId: string | null): string {
  if (selectedCategoryId) {
    const categoryItem = catalogCategory(catalog, selectedCategoryId);
    const blocks = catalogBlocksForCategory(catalog, selectedCategoryId);
    return `
      <div class="catalog-nav">
        <button type="button" class="tiny-button" data-catalog-back>返回分类</button>
        <span>${escapeHtml(categoryItem?.displayName ?? '分类')}</span>
      </div>
      <div class="catalog-list catalog-block-list">
        ${blocks.length > 0 ? blocks.map((blockItem) => `
          <button type="button" class="catalog-block" data-catalog-block="${escapeAttr(blockItem.id)}">
            <b>${escapeHtml(blockItem.displayName)}</b>
            <span>${escapeHtml(blockItem.description)}</span>
            <small>${escapeHtml(capabilityLabel(blockItem.simulationCapability))}</small>
          </button>
        `).join('') : '<p class="catalog-empty">API 未连接，积木目录暂不可用。</p>'}
      </div>
    `;
  }

  const categories = catalogCategories(catalog);
  return `
    <div class="catalog-list">
      ${categories.map((categoryItem) => {
        const count = catalogBlocksForCategory(catalog, categoryItem.id).length;
        return `
          <button type="button" class="catalog-category" data-catalog-category="${escapeAttr(categoryItem.id)}">
            <b>${escapeHtml(categoryItem.displayName)}</b>
            <span>${escapeHtml(categoryItem.description)}</span>
            <small>${count > 0 ? `${count} 个积木` : 'API 未连接'}</small>
          </button>
        `;
      }).join('')}
    </div>
  `;
}

function capabilityLabel(value: string): string {
  switch (value) {
    case 'FULLY_SIMULATABLE':
      return '可模拟';
    case 'APPROXIMATE_SIMULATION':
      return '近似模拟';
    case 'REQUIRES_MINECRAFT_RUNTIME':
      return '需要游戏运行时';
    default:
      return '目录积木';
  }
}
