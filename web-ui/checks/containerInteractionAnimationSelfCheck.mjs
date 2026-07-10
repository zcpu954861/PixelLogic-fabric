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
assert.match(app, /captureBlockRects\(/, 'graph edits must capture first rects');
assert.match(app, /playGraphTransition\(/, 'graph edits must play the final FLIP transition');
assert.match(app, /catalogDragGhostRect\(/, 'catalog drops must preserve the live ghost origin');

const pointerMove = app.slice(app.indexOf('function moveBlockDrag'), app.indexOf('function endBlockDrag'));
assert.doesNotMatch(pointerMove, /applyGraphEdit\(/, 'pointer move must not commit graph state');

assert.match(animations, /interactionAnimationTokens/, 'animation timing must use unified tokens');
assert.match(animations, /cancelInteractionAnimations/, 'running animations must have a central cancel path');
assert.match(animations, /clearPlacementPreview/, 'candidate preview must have a central cleanup path');
assert.match(animations, /animation\.finished\.catch/, 'cancelled animations must not leak rejected promises');
assert.match(animations, /container-resize-guide/, 'container resize must use a visual-only guide');
assert.doesNotMatch(animations + ghost, /setTimeout\(/, 'animation modules must not queue timeout chains');
assert.doesNotMatch(animations + ghost, /outerHTML/, 'animation modules must not restore DOM through outerHTML');

assert.match(ghost, /renderBlock\(/, 'catalog ghost must reuse the real block renderer');
assert.match(styles, /prefers-reduced-motion:\s*reduce/, 'reduced-motion fallback must exist');
assert.match(styleIndex, /interaction-animations\.css/, 'animation styles must be loaded');
assert.doesNotMatch(graphTypes, /animationState|ghostRect|interactionTransition/, 'animation state must not enter graph types');

console.log('container interaction animation self-check passed');
