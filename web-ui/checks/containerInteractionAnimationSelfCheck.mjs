import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

const app = read('src/ui/app.ts');
const animations = read('src/ui/canvas/interactionAnimations.ts');
const ghost = read('src/ui/canvas/dragGhostView.ts');
const styles = read('src/styles/interaction-animations.css');
const styleIndex = read('src/styles/index.css');
const graphTypes = read('src/model/graphTypes.ts');

assert.match(app, /from '\.\/canvas\/interactionAnimations'/, 'app must use the interaction animation controller');
assert.match(app, /from '\.\/canvas\/dragGhostView'/, 'app must use the catalog drag ghost');
assert.match(app, /showPlacementPreview\(/, 'candidate preview must use placement-driven animation');
assert.match(app, /activeInsertDecorationKey/, 'unchanged candidates must keep stable decorations');
assert.match(app, /captureBlockRects\(/, 'graph edits must capture first rects');
assert.match(app, /playGraphTransition\(/, 'graph edits must play the final FLIP transition');
assert.match(app, /catalogDragGhostRect\(/, 'catalog drops must preserve the live ghost origin');

const pointerMove = app.slice(app.indexOf('function moveBlockDrag'), app.indexOf('function endBlockDrag'));
assert.doesNotMatch(pointerMove, /applyGraphEdit\(/, 'pointer move must not commit graph state');

assert.match(animations, /interactionAnimationTokens/, 'animation timing must use unified tokens');
assert.match(animations, /cancelInteractionAnimations/, 'running animations must have a central cancel path');
assert.match(animations, /clearPlacementPreview/, 'candidate preview must have a central cleanup path');
assert.match(animations, /previewLayoutSignature/, 'unchanged placement layouts must not restart previews');
assert.match(animations, /animation\.finished\.catch/, 'cancelled animations must not leak rejected promises');
assert.match(animations, /animateArtifactResize/, 'container insertion preview must visibly stretch the real shape');
assert.match(animations, /draggedNodeIds\.forEach/, 'placement preview must render the complete dragged chain');
assert.match(animations, /renderBlock\(\{ \.\.\.block, selected: false \}/, 'placement ghosts must reuse the real block renderer');
assert.doesNotMatch(animations + styles, /container-resize-guide/, 'container animation must not add a duplicate outline guide');
assert.doesNotMatch(animations, /drop-placeholder/, 'placement preview must not fall back to an overlapping dashed box');
assert.doesNotMatch(animations + ghost, /setTimeout\(/, 'animation modules must not queue timeout chains');
assert.doesNotMatch(animations + ghost, /outerHTML/, 'animation modules must not restore DOM through outerHTML');

assert.match(ghost, /renderBlock\(/, 'catalog ghost must reuse the real block renderer');
assert.match(styles, /prefers-reduced-motion:\s*reduce/, 'reduced-motion fallback must exist');
assert.match(
  styles,
  /\.container-preview-shell \.puzzle-shape \.block-body\s*\{[^}]*stroke-dasharray:\s*none/s,
  'container stretch preview must use a solid block outline',
);
assert.match(styles, /\.placement-ghost-block\s*\{[^}]*opacity:/s, 'placement ghosts must remain translucent');
assert.match(styleIndex, /interaction-animations\.css/, 'animation styles must be loaded');
assert.doesNotMatch(graphTypes, /animationState|ghostRect|interactionTransition/, 'animation state must not enter graph types');

console.log('container interaction animation self-check passed');
