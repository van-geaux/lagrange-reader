const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

class FakeElement {
    constructor(tagName, rect = { left: 0, top: 0, width: 800, height: 1200 }) {
        this.tagName = tagName.toUpperCase();
        this.rect = rect;
        this.style = {};
    }
    closest(selector) {
        return selector.split(',').some((value) => value.trim().toUpperCase() === this.tagName)
            ? this
            : null;
    }
    getBoundingClientRect() { return this.rect; }
    getAttribute() { return ''; }
}

function loadGestureScript({ images, text = '', interactive = false }) {
    const listeners = new Map();
    const document = {
        baseURI: 'OPS/page.xhtml',
        body: { innerText: text },
        documentElement: { dataset: {} },
        querySelectorAll: () => images,
        querySelector: () => interactive ? {} : null,
        addEventListener: (type, listener) => listeners.set(type, listener)
    };
    const window = { innerWidth: 800, innerHeight: 1200 };
    const context = {
        Element: FakeElement,
        document,
        window,
        setTimeout,
        clearTimeout,
        Math,
        Map
    };
    vm.runInNewContext(
        fs.readFileSync('app/src/main/assets/epub-image-gesture.js', 'utf8'),
        context
    );
    return { api: window.__bookorbitImageZoomTestApi, listeners };
}

const image = new FakeElement('img');
const imageOnlyHarness = loadGestureScript({ images: [image] });
const imageOnly = imageOnlyHarness.api;
assert.ok(imageOnly, 'zoom test API must be installed');
assert.equal(imageOnly.isImageOnlyDocument(), true);
assert.equal(imageOnly.clampScale(0.5), 1);
assert.equal(imageOnly.clampScale(8), 4);

function touchEvent(touches, target = image) {
    return {
        target,
        touches,
        prevented: false,
        stopped: false,
        preventDefault() { this.prevented = true; },
        stopImmediatePropagation() { this.stopped = true; }
    };
}

const singleTouch = touchEvent([{ clientX: 100, clientY: 100 }]);
imageOnlyHarness.listeners.get('touchstart')(singleTouch);
assert.equal(singleTouch.prevented, false, '1x single-touch input must remain with Readium');

const pinchStart = touchEvent([
    { clientX: 100, clientY: 100 },
    { clientX: 200, clientY: 100 }
]);
imageOnlyHarness.listeners.get('touchstart')(pinchStart);
assert.equal(pinchStart.prevented, true, 'image-only pinch must be claimed');
const pinchMove = touchEvent([
    { clientX: 50, clientY: 100 },
    { clientX: 250, clientY: 100 }
]);
imageOnlyHarness.listeners.get('touchmove')(pinchMove);
assert.equal(imageOnly.currentScale(), 2);
assert.equal(pinchMove.prevented, true);

const mixedHarness = loadGestureScript({ images: [image], text: 'Chapter text' });
assert.equal(mixedHarness.api.isImageOnlyDocument(), false);
const mixedPinch = touchEvent([
    { clientX: 100, clientY: 100 },
    { clientX: 200, clientY: 100 }
]);
mixedHarness.listeners.get('touchstart')(mixedPinch);
assert.equal(mixedPinch.prevented, false, 'mixed-content pinch must remain with Readium');
assert.equal(loadGestureScript({ images: [image, new FakeElement('img')] }).api.isImageOnlyDocument(), false);
assert.equal(loadGestureScript({ images: [image], interactive: true }).api.isImageOnlyDocument(), false);

console.log('epub image gesture tests: PASS');
