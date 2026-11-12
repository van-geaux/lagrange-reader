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
        if (selector === 'a[href]') return this.anchor || null;
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
        baseURI: 'https://readium/publication/OPS/page.xhtml',
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
        Map,
        URL
    };
    vm.runInNewContext(
        fs.readFileSync('app/src/main/assets/epub-image-gesture.js', 'utf8'),
        context
    );
    return { api: window.__bookorbitImageZoomTestApi, listeners, window };
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

const linkedImage = new FakeElement('img');
const internalLinkHarness = loadGestureScript({ images: [linkedImage] });
const internalAnchor = {
    href: 'https://readium/publication/OPS/toc.xhtml',
    getAttribute: () => 'toc.xhtml',
    clicks: 0,
    click() {
        this.clicks += 1;
        const resumedEvent = clickEvent(linkedImage);
        internalLinkHarness.listeners.get('click')(resumedEvent);
        assert.equal(resumedEvent.prevented, false, 'confirmed image link click must resume');
    }
};
linkedImage.anchor = internalAnchor;
function clickEvent(target) {
    return {
        target,
        prevented: false,
        stopped: false,
        preventDefault() { this.prevented = true; },
        stopImmediatePropagation() { this.stopped = true; }
    };
}
const interceptedImageClick = clickEvent(linkedImage);
internalLinkHarness.listeners.get('click')(interceptedImageClick);
assert.equal(interceptedImageClick.prevented, true, 'internal linked-image click must wait for confirmation');
assert.equal(interceptedImageClick.stopped, true);
assert.deepEqual(JSON.parse(internalLinkHarness.window.__bookorbitImageLink), { gesture: 'image_link' });
assert.equal(internalAnchor.clicks, 0, 'link must not open before confirmation');
assert.equal(internalLinkHarness.window.__bookorbitResumeImageLinkClick(), true);
assert.equal(internalAnchor.clicks, 1, 'confirmed action must resume the original image link');

const cancelledImage = new FakeElement('img');
const cancelledHarness = loadGestureScript({ images: [cancelledImage] });
const cancelledAnchor = {
    href: 'https://readium/publication/OPS/toc.xhtml',
    getAttribute: () => 'toc.xhtml',
    clicks: 0,
    click() { this.clicks += 1; }
};
cancelledImage.anchor = cancelledAnchor;
cancelledHarness.listeners.get('click')(clickEvent(cancelledImage));
cancelledHarness.window.__bookorbitCancelImageLinkClick();
assert.equal(cancelledHarness.window.__bookorbitResumeImageLinkClick(), false);
assert.equal(cancelledAnchor.clicks, 0, 'cancelled image link must remain on the current page');

const externalImage = new FakeElement('img');
const externalHarness = loadGestureScript({ images: [externalImage] });
externalImage.anchor = {
    href: 'https://example.org/page',
    getAttribute: () => 'https://example.org/page'
};
const externalClick = clickEvent(externalImage);
externalHarness.listeners.get('click')(externalClick);
assert.equal(externalClick.prevented, false, 'external image links must retain normal browser handling');

const unlinkedImage = new FakeElement('img');
const unlinkedHarness = loadGestureScript({ images: [unlinkedImage] });
const unlinkedClick = clickEvent(unlinkedImage);
unlinkedHarness.listeners.get('click')(unlinkedClick);
assert.equal(unlinkedClick.prevented, false, 'unlinked image taps must remain unchanged');

console.log('epub image gesture tests: PASS');
