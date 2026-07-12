import {
  catalogBlocksForCategory,
  catalogCategoriesForPack,
  catalogPack,
  catalogPacks,
  sanitizeLibraryLocation,
  searchCatalog,
} from '../../model/blockCatalog';
import type { BlockCatalog, CatalogBlock, LibraryFilter, LibraryLocation } from '../../model/graphTypes';
import { escapeAttr, escapeHtml } from '../../utils/dom';

export type CatalogLibraryViewState = {
  location: LibraryLocation;
  query: string;
  filter: LibraryFilter | null;
};

export function renderCatalogLibrary(catalog: BlockCatalog, view: CatalogLibraryViewState): string {
  return `
    <div class="catalog-search">
      <label for="block-library-search">搜索全部积木</label>
      <input
        id="block-library-search"
        type="search"
        data-library-search
        value="${escapeAttr(view.query)}"
        placeholder="名称、别名或 blockId"
        aria-label="搜索全部积木"
        autocomplete="off"
      >
    </div>
    ${view.filter ? `
      <div class="catalog-filter" role="status">
        <span>${escapeHtml(view.filter.label)}</span>
        <button type="button" class="tiny-button" data-library-filter-clear>退出筛选</button>
      </div>
    ` : ''}
    <div class="catalog-browser" data-library-browser>
      ${renderCatalogLibraryBrowser(catalog, view)}
    </div>
  `;
}

export function renderCatalogLibraryBrowser(catalog: BlockCatalog, view: CatalogLibraryViewState): string {
  const location = sanitizeLibraryLocation(catalog, view.location, view.filter);
  return view.query.trim()
    ? renderSearchResults(catalog, view.query, view.filter)
    : renderLocation(catalog, location, view.filter);
}

function renderLocation(catalog: BlockCatalog, location: LibraryLocation, filter: LibraryFilter | null): string {
  if (location.level === 'categories') {
    return renderCategories(catalog, location.packId, filter);
  }
  if (location.level === 'blocks') {
    return renderBlocks(catalog, location.packId, location.categoryId, filter);
  }
  return renderPacks(catalog, filter);
}

function renderPacks(catalog: BlockCatalog, filter: LibraryFilter | null): string {
  const packs = catalogPacks(catalog, filter);
  return `
    <div class="catalog-nav"><strong>全部积木包</strong></div>
    <div class="catalog-list">
      ${packs.length ? packs.map((pack) => `
        <button type="button" class="catalog-category catalog-pack" data-library-pack="${escapeAttr(pack.id)}">
          <span class="catalog-icon" aria-hidden="true">${renderCatalogIcon(pack.id, pack.icon)}</span>
          <b>${escapeHtml(pack.displayName)}</b>
          <span class="catalog-description">${escapeHtml(pack.description)}</span>
          <small>${pack.count} 个积木</small>
        </button>
      `).join('') : '<p class="catalog-empty">当前没有可显示的积木。</p>'}
    </div>
  `;
}

function renderCategories(catalog: BlockCatalog, packId: string, filter: LibraryFilter | null): string {
  const pack = catalogPack(catalog, packId);
  const categories = catalogCategoriesForPack(catalog, packId, filter);
  return `
    <div class="catalog-nav">
      <button type="button" class="tiny-button" data-library-root>‹ 全部积木包</button>
      <strong>${escapeHtml(pack?.displayName ?? '积木包')}</strong>
    </div>
    <div class="catalog-list">
      ${categories.map((category) => `
        <button type="button" class="catalog-category" data-library-category="${escapeAttr(category.id)}" data-library-pack-id="${escapeAttr(packId)}">
          <span class="catalog-icon" aria-hidden="true">${renderCatalogIcon(category.id, category.icon)}</span>
          <b>${escapeHtml(category.displayName)}</b>
          <span class="catalog-description">${escapeHtml(category.description)}</span>
          <small>${category.count} 个积木</small>
        </button>
      `).join('') || '<p class="catalog-empty">这个积木包当前没有可用分类。</p>'}
    </div>
  `;
}

function renderBlocks(catalog: BlockCatalog, packId: string, categoryId: string, filter: LibraryFilter | null): string {
  const pack = catalogPack(catalog, packId);
  const category = catalog.categories.find((item) => item.id === categoryId);
  const blocks = catalogBlocksForCategory(catalog, categoryId, filter);
  return `
    <nav class="catalog-breadcrumb" aria-label="积木库路径">
      <button type="button" data-library-root>全部积木包</button><span aria-hidden="true">›</span>
      <button type="button" data-library-pack="${escapeAttr(packId)}">${escapeHtml(pack?.displayName ?? '积木包')}</button><span aria-hidden="true">›</span>
      <strong>${escapeHtml(category?.displayName ?? '分类')}</strong>
    </nav>
    <button type="button" class="tiny-button catalog-back" data-library-pack="${escapeAttr(packId)}">‹ 返回${escapeHtml(pack?.displayName ?? '积木包')}</button>
    <div class="catalog-list catalog-block-list">
      ${blocks.map((block) => renderBlock(block)).join('') || '<p class="catalog-empty">这个分类当前没有可用积木。</p>'}
    </div>
  `;
}

function renderSearchResults(catalog: BlockCatalog, query: string, filter: LibraryFilter | null): string {
  const results = searchCatalog(catalog, query, filter);
  return `
    <div class="catalog-nav"><strong>全局搜索</strong><span>${results.length} 个结果</span></div>
    <div class="catalog-list catalog-block-list">
      ${results.map((result) => renderBlock(result.block, result.path)).join('')
        || `<p class="catalog-empty">没有找到“${escapeHtml(query.trim())}”相关的积木。</p>`}
    </div>
  `;
}

function renderBlock(block: CatalogBlock, path = ''): string {
  return `
    <button type="button" class="catalog-block" data-catalog-block="${escapeAttr(block.id)}">
      <b>${escapeHtml(block.displayName)}</b>
      <span class="catalog-description">${escapeHtml(block.description)}</span>
      ${path ? `<em>${escapeHtml(path)}</em>` : ''}
      <small>${escapeHtml(capabilityLabel(block.simulationCapability))}</small>
    </button>
  `;
}

function capabilityLabel(value: string): string {
  switch (value) {
    case 'FULLY_SIMULATABLE': return '可模拟';
    case 'APPROXIMATE_SIMULATION': return '近似模拟';
    case 'REQUIRES_MINECRAFT_RUNTIME': return '需要游戏运行时';
    default: return '目录积木';
  }
}

const catalogIconMarkup: Readonly<Record<string, string>> = {
  'events-triggers': '<path class="catalog-icon-fill" d="M11.6 1.8 4.9 10.7h4.2l-.7 7.5 6.8-9.5h-4.1z"/>',
  'logic-flow': '<path d="M5 5.2h7.3a3 3 0 0 1 3 3v.6M15.3 6.5v2.3H13M15 14.8H7.7a3 3 0 0 1-3-3v-.6M4.7 13.5v-2.3H7"/>',
  'player-entity': '<circle cx="10" cy="5.4" r="2.3"/><path d="M5.7 15.8v-2.1c0-2.1 1.9-3.8 4.3-3.8s4.3 1.7 4.3 3.8v2.1M3.2 13.2h2.5m8.6 0h2.5"/><rect x="2.2" y="12.2" width="2" height="2" rx=".4"/><rect x="15.8" y="12.2" width="2" height="2" rx=".4"/>',
  'location-region': '<path d="m10 2.2 4.8 2.9v4.2L10 17.7 5.2 9.3V5.1z"/><circle cx="10" cy="7.4" r="1.8"/>',
  'block-world': '<path d="m10 2.7 6 3.4v7L10 16.7l-6-3.6v-7zM4 6.1l6 3.5 6-3.5M10 9.6v7.1"/>',
  'presentation-feedback': '<path d="M10 2.2c.5 4.7 3.1 7.3 7.8 7.8-4.7.5-7.3 3.1-7.8 7.8-.5-4.7-3.1-7.3-7.8-7.8 4.7-.5 7.3-3.1 7.8-7.8Z"/><path d="M16.1 2.7v2.6m1.3-1.3h-2.6"/>',
  'state-data': '<rect x="3" y="3" width="14" height="14" rx="2"/><path d="M6 7h8M6 10h8M6 13h5"/><circle class="catalog-icon-fill" cx="5" cy="7" r=".6"/><circle class="catalog-icon-fill" cx="5" cy="10" r=".6"/><circle class="catalog-icon-fill" cx="5" cy="13" r=".6"/>',
  'events-triggers.test-entry': '<path d="M5 3.5v13M7.8 6.2l6.8 3.8-6.8 3.8z"/>',
  'logic-flow.loops': '<path d="M5.1 6.2A6.1 6.1 0 0 1 15.6 7M15.6 3.8V7h-3.2M14.9 13.8A6.1 6.1 0 0 1 4.4 13M4.4 16.2V13h3.2"/>',
  'logic-flow.timing': '<circle cx="10" cy="10" r="6.8"/><path d="M10 5.8v4.5l3 1.8M7.5 2.2h5"/>',
  'player-entity.tags': '<path d="m3.2 5.1 7.2-2 6.4 6.4-7.3 7.3-6.3-6.4z"/><circle cx="7" cy="7" r="1.2"/>',
  'player-entity.identity-permissions': '<path d="M10 2.6 16 5v4.4c0 3.8-2.4 6.5-6 8-3.6-1.5-6-4.2-6-8V5z"/><circle cx="10" cy="7.2" r="1.7"/><path d="M6.9 12.2c.7-1.4 1.7-2.1 3.1-2.1s2.4.7 3.1 2.1"/>',
  'player-entity.execution-context': '<path d="M6.7 4H4v12h2.7M13.3 4H16v12h-2.7M6.8 10h6.4M10.8 7.6l2.4 2.4-2.4 2.4"/>',
  'location-region.dimensions-heights': '<path d="M6 3v14M3.8 5.2 6 3l2.2 2.2M3.8 14.8 6 17l2.2-2.2M11 5h5M11 10h3.5M11 15h5"/>',
  'location-region.regions': '<path d="M7 3H3v4M13 3h4v4M17 13v4h-4M7 17H3v-4"/><rect x="7" y="7" width="6" height="6" rx="1"/>',
  'location-region.spatial-relations': '<circle cx="4" cy="13.5" r="1.8"/><circle cx="16" cy="6.5" r="1.8"/><path d="m5.7 12.5 8.6-5M7.1 6.5h4.5M9.8 4.7l1.8 1.8-1.8 1.8"/>',
  'block-world.target-block': '<path d="m8.8 3.4 5.5 3.1v6.3L8.8 16l-5.5-3.2V6.5zM3.3 6.5l5.5 3.2 5.5-3.2M8.8 9.7V16"/><circle class="catalog-icon-fill" cx="15.9" cy="14.8" r="1.6"/>',
  'presentation-feedback.player-messages': '<path d="M3 4.2h14v9.2H8l-4.2 3v-3H3z"/><path d="M6 7.4h8M6 10.2h5"/>',
  'presentation-feedback.screen-prompts': '<rect x="2.8" y="3.5" width="14.4" height="11" rx="1.8"/><path d="M7 17h6M10 14.5V17M6 7h8M6 10h5"/>',
  'presentation-feedback.diagnostics': '<rect x="3" y="3" width="14" height="14" rx="2"/><path d="M5.5 11h2l1.4-4 2.2 7 1.5-4h1.9"/>',
  'state-data.conditions': '<path d="M4 4h12M4 9h7M4 14h5"/><path d="m12 13.2 1.6 1.6 3-3.4"/>',
  'state-data.mutations': '<path d="M4 5h8M4 10h12M4 15h6"/><path d="M15 3v4M13 5h4M13 13v4M11 15h4"/>',
};

export function renderCatalogIcon(id: string, fallback: string): string {
  const markup = catalogIconMarkup[id];
  return markup
    ? `<svg viewBox="0 0 20 20" focusable="false" aria-hidden="true">${markup}</svg>`
    : escapeHtml(fallback);
}
