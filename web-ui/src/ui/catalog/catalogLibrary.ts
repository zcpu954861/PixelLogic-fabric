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
          <span class="catalog-icon" aria-hidden="true">${escapeHtml(pack.icon)}</span>
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
          <span class="catalog-icon" aria-hidden="true">${escapeHtml(category.icon)}</span>
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
