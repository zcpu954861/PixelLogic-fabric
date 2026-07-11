import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createServer } from 'vite';

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' });
try {
  const catalogModel = await server.ssrLoadModule('/src/model/blockCatalog.ts');
  const { renderCatalogLibrary } = await server.ssrLoadModule('/src/ui/catalog/catalogLibrary.ts');
  const pack = (id, order) => ({ id, displayName: id === 'players' ? '玩家与实体' : '逻辑与流程', description: `${id} pack`, icon: '◇', order });
  const category = (id, packId, order) => ({ id, packId, displayName: id.includes('tags') ? '标签' : '循环', description: `${id} category`, icon: '·', order, visibleByDefault: true });
  const block = (id, categoryId, visibility = 'BROWSE', capabilities = [], aliases = [], searchKeywords = []) => ({
    id, version: 1, displayName: id === 'action.player.tag' ? '添加玩家标签' : id === 'context.tag' ? '上下文标签条件' : id,
    description: `${id} description`, categoryId, subcategoryId: categoryId, tags: [], capabilities, nodeKind: id.includes('condition') || id === 'context.tag' ? 'condition' : 'action',
    nodeType: id.includes('condition') || id === 'context.tag' ? 'PLAYER_HAS_TAG_CONDITION' : 'PLAYER_ADD_TAG_ACTION',
    defaultConfig: {}, formSchema: [], summaryTemplate: id, summaryFormatter: '', predicateSummaryTemplate: '', predicateNegatedSummaryTemplate: '',
    containerSlots: [], inputSlots: [], outputSlots: [], simulationCapability: 'FULLY_SIMULATABLE', mcCapability: 'REQUIRES_MINECRAFT_RUNTIME',
    safetyFlags: [], deprecated: false, hidden: visibility !== 'BROWSE', visibility, aliases, searchKeywords,
  });
  const catalog = {
    packs: [pack('players', 20), pack('logic', 10), pack('empty', 30)],
    categories: [category('players.tags', 'players', 10), category('logic.loops', 'logic', 10), category('empty.none', 'empty', 10)],
    subcategories: [],
    blocks: [
      block('action.player.tag', 'players.tags', 'BROWSE', [], ['action.player.tag.legacy'], ['加标签', '玩家 标签']),
      block('condition.player.tag', 'players.tags', 'BROWSE', ['PREDICATE'], [], ['标签判断', 'has tag']),
      block('context.tag', 'players.tags', 'CONTEXT_ONLY', ['PREDICATE'], [], ['上下文条件', 'entity as']),
      block('hidden.internal', 'players.tags', 'HIDDEN', ['PREDICATE'], [], ['内部']),
      block('control.loop', 'logic.loops', 'BROWSE', [], [], ['循环', 'repeat while until']),
    ],
  };

  const firstIndex = catalogModel.catalogIndex(catalog);
  assert.equal(firstIndex, catalogModel.catalogIndex(catalog), 'one snapshot identity must reuse one index');
  assert.deepEqual(catalogModel.catalogPacks(catalog).map(({ id, count }) => [id, count]), [['logic', 1], ['players', 2]], 'empty packs and context-only blocks stay out of normal counts');
  assert.deepEqual(catalogModel.catalogCategoriesForPack(catalog, 'players').map(({ id, count }) => [id, count]), [['players.tags', 2]], 'category count must use deduplicated browse blocks');
  assert.deepEqual(catalogModel.catalogBlocksForCategory(catalog, 'players.tags').map((item) => item.id), ['action.player.tag', 'condition.player.tag']);

  const predicateFilter = { capability: 'PREDICATE', label: '当前条件槽可用' };
  assert.deepEqual(catalogModel.catalogBlocksForCategory(catalog, 'players.tags', predicateFilter).map((item) => item.id), ['condition.player.tag', 'context.tag'], 'slot filter must intersect capability with non-hidden visibility');
  assert.equal(catalogModel.catalogPacks(catalog, predicateFilter).find((item) => item.id === 'players')?.count, 2);

  assert.equal(catalogModel.searchCatalog(catalog, 'action.player.tag.legacy')[0]?.block.id, 'action.player.tag', 'exact legacy alias must search globally');
  assert.equal(catalogModel.searchCatalog(catalog, '加标签')[0]?.block.id, 'action.player.tag', 'human search keywords must search globally');
  assert.equal(catalogModel.catalogBlock(catalog, 'action.player.tag.legacy')?.id, 'action.player.tag', 'legacy aliases must still resolve old block ids');
  assert.equal(catalogModel.searchCatalog(catalog, 'ACTION.PLAYER.TAG')[0]?.block.id, 'action.player.tag', 'blockId search must ignore English case');
  assert.equal(catalogModel.searchCatalog(catalog, '玩家   标签')[0]?.block.id, 'action.player.tag', 'multiple normalized terms must all match one prebuilt document');
  assert.equal(catalogModel.searchCatalog(catalog, '上下文条件').length, 0, 'context-only block must not appear in ordinary search');
  assert.equal(catalogModel.searchCatalog(catalog, '上下文条件', predicateFilter)[0]?.path, '玩家与实体 > 标签', 'filtered result must include its formal path');
  assert.equal(catalogModel.searchCatalog(catalog, '内部', predicateFilter).length, 0, 'hidden block must never appear');

  const rankingCatalog = {
    packs: [pack('z-pack', 10), pack('a-pack', 10)],
    categories: [category('z-pack.group', 'z-pack', 10), category('a-pack.z', 'a-pack', 10), category('a-pack.a', 'a-pack', 10)],
    subcategories: [],
    blocks: [
      { ...block('z.contains', 'z-pack.group', 'BROWSE', [], [], ['needle']), displayName: 'other' },
      { ...block('a.prefix', 'a-pack.z'), displayName: 'needle prefix' },
      { ...block('a.alias', 'a-pack.a', 'BROWSE', [], ['needle']), displayName: 'other' },
      { ...block('z.exact', 'z-pack.group'), displayName: 'needle' },
      { ...block('a.exact.z', 'a-pack.z'), displayName: 'needle' },
      { ...block('a.exact.a', 'a-pack.a'), displayName: 'needle' },
      { ...block('a.exact.b', 'a-pack.a'), displayName: 'needle' },
    ],
  };
  assert.deepEqual(catalogModel.searchCatalog(rankingCatalog, 'needle').map((item) => item.block.id),
    ['a.alias', 'a.exact.a', 'a.exact.b', 'a.exact.z', 'z.exact', 'a.prefix', 'z.contains'],
    'search should rank exact, prefix, and contains matches before stable pack/category/id tie-breaks');

  assert.deepEqual(catalogModel.sanitizeLibraryLocation(catalog, { level: 'blocks', packId: 'players', categoryId: 'missing' }), { level: 'categories', packId: 'players' });
  assert.deepEqual(catalogModel.sanitizeLibraryLocation(catalog, { level: 'categories', packId: 'missing' }), { level: 'packs' });
  assert.deepEqual(catalogModel.sanitizeLibraryLocation(catalog, { level: 'blocks', packId: 'logic', categoryId: 'logic.loops' }, predicateFilter), { level: 'packs' }, 'filter must hide locations with no compatible blocks');

  const packHtml = renderCatalogLibrary(catalog, { location: { level: 'packs' }, query: '', filter: null });
  assert.match(packHtml, /data-library-pack="players"/);
  assert.doesNotMatch(packHtml, /data-library-pack="empty"/);
  assert.match(packHtml, /aria-label="搜索全部积木"/);
  const categoryHtml = renderCatalogLibrary(catalog, { location: { level: 'categories', packId: 'players' }, query: '', filter: null });
  assert.match(categoryHtml, /data-library-root/);
  assert.match(categoryHtml, /data-library-category="players\.tags"/);
  const blockHtml = renderCatalogLibrary(catalog, { location: { level: 'blocks', packId: 'players', categoryId: 'players.tags' }, query: '', filter: null });
  assert.match(blockHtml, /aria-label="积木库路径"/);
  assert.match(blockHtml, /data-catalog-block="action\.player\.tag"/);
  assert.doesNotMatch(blockHtml, /data-catalog-(?:pack|category)/, 'drag/create identity must remain blockId-only');
  const searchHtml = renderCatalogLibrary(catalog, { location: { level: 'blocks', packId: 'logic', categoryId: 'logic.loops' }, query: '加标签', filter: null });
  assert.match(searchHtml, /玩家与实体 &gt; 标签/, 'search must cross packs without changing browse location');

  const node = catalogModel.createCatalogNode(catalog.blocks[0], 'node', { x: 1, y: 2 });
  for (const forbidden of ['packId', 'categoryId', 'subcategoryId', 'visibility', 'hidden', 'aliases', 'searchKeywords']) assert.equal(forbidden in node, false, `Graph node must not include ${forbidden}`);
  assert.equal(node.blockId, 'action.player.tag');

  const app = readFileSync(new URL('../src/ui/app.ts', import.meta.url), 'utf8');
  const layoutCss = readFileSync(new URL('../src/styles/layout.css', import.meta.url), 'utf8');
  assert.match(app, /libraryFilterRestoreLocation = state\.libraryLocation/);
  assert.match(app, /state\.libraryFilterRestoreLocation \?\? state\.libraryLocation/);
  assert.match(app, /sanitizeLibraryLocation\(state\.catalog/);
  assert.match(app, /event\.target !== blockEl/, 'nested rack buttons must keep their own keyboard behavior');
  assert.match(app, /button\.addEventListener\('keydown',[\s\S]*addCatalogBlockAt\(button\.dataset\.catalogBlock, null\)/,
    'catalog blocks must support Enter/Space without changing their pointer drag payload');
  const catalogPointer = app.slice(app.indexOf('function beginCatalogPointer'), app.indexOf('function addCatalogBlockAt'));
  assert.match(catalogPointer, /findInsertCandidate\(catalogDragGraph, catalogDrag, activeCatalog\(\)\)/,
    'direct library drag must reuse normal canvas candidate recognition');
  assert.match(catalogPointer, /computeDragDrop\(catalogDragGraph, catalogDrag\)/,
    'direct library drop must reuse normal canvas placement on pointerup');
  assert.match(app, /querySelector<HTMLButtonElement>\('\[data-library-category\]'\)\?\.focus\(\)/,
    'keyboard navigation must move focus into the destination level');
  const libraryBinder = app.slice(app.indexOf('function bindCatalogLibraryInteractions'), app.indexOf('function refreshCatalogSearch'));
  assert.match(libraryBinder, /root\.querySelectorAll/);
  assert.doesNotMatch(libraryBinder, /document\.querySelector/, 'library rebinding must stay scoped to its local root');
  const searchRefresh = app.slice(app.indexOf('function refreshCatalogSearch'), app.indexOf('function activatePredicateLibraryFilter'));
  assert.match(searchRefresh, /browser\.innerHTML = renderCatalogLibraryBrowser/);
  assert.doesNotMatch(searchRefresh, /root\.innerHTML|renderCatalogLibrary\(/,
    'typing must replace only search results and preserve the input DOM and native undo history');
  assert.match(app, /document\.activeElement\?\.matches\('\[data-library-search\]'\)/,
    'autosave completion must not replace a focused search input or active IME composition');
  assert.match(app, /if \(editableTarget && \(isUndoShortcut\(event\) \|\| isRedoShortcut\(event\)\)\)/,
    'library search must keep native text undo/redo shortcuts');
  assert.match(layoutCss, /\.catalog-description\s*\{/, 'catalog descriptions must have a dedicated selector');
  assert.doesNotMatch(layoutCss, /\.catalog-category span\s*,/, 'description rules must not override pack/category icons');
  const pollingRefresh = app.slice(app.indexOf('function refreshTestExecutionView'), app.indexOf('function apiBusyAttr'));
  assert.doesNotMatch(pollingRefresh, /libraryLocation|libraryQuery|renderCatalogLibrary|renderApp\(/, 'polling local refresh must not reset library state');
  const blockView = readFileSync(new URL('../src/ui/canvas/blockView.ts', import.meta.url), 'utf8');
  assert.match(blockView, /data-library-predicate-filter/);
  assert.match(blockView, /aria-label="打开当前条件槽可用积木"/);
  assert.match(app, /targetContainerId, targetSlotId/);
  assert.match(app, /conditionSlotRects\(graph, filterContainer, state\.libraryFilter\.targetSlotId\)/,
    'keyboard activation in a filtered rack must target the slot that opened the library');

  console.log('block library taxonomy WebUI self-check passed');
} finally {
  await server.close();
}
