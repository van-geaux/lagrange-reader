(() => {
    const stateKey = "__bookorbitImageGesture";
    if (document.documentElement.dataset.bookorbitImageGestures === "1") return null;
    document.documentElement.dataset.bookorbitImageGestures = "1";
    let longPressTimer = null;
    let longPressTarget = null;
    let activePointerId = null;
    let startX = 0;
    let startY = 0;
    let suppressNextClick = false;
    const movementLimit = 12;
    const minimumScale = 1;
    const maximumScale = 4;
    let zoomTarget = null;
    let scale = minimumScale;
    let panX = 0;
    let panY = 0;
    let baseWidth = 0;
    let baseHeight = 0;
    let pinchStartDistance = 0;
    let pinchStartScale = minimumScale;
    let pinchStartCenterX = 0;
    let pinchStartCenterY = 0;
    let panStartX = 0;
    let panStartY = 0;
    let panOriginX = 0;
    let panOriginY = 0;
    let zoomGestureActive = false;
    const imageFor = (event) => {
        const target = event.target;
        return target instanceof Element ? target.closest("img, svg") : null;
    };
    const visibleImageElements = () => Array.from(document.querySelectorAll("img, svg"))
        .filter((element) => {
            const rect = element.getBoundingClientRect();
            return rect.width > 0 && rect.height > 0;
        });
    const isImageOnlyDocument = () => {
        const images = visibleImageElements();
        const meaningfulText = (document.body ? document.body.innerText || "" : "").replace(/\s+/g, "");
        const interactive = document.querySelector(
            "a[href], button, input, select, textarea, video, audio, iframe, [contenteditable='true']"
        );
        return images.length === 1 && meaningfulText.length === 0 && interactive === null;
    };
    const imageOnlyTarget = () => isImageOnlyDocument() ? visibleImageElements()[0] : null;
    const clampScale = (candidate) => Math.min(maximumScale, Math.max(minimumScale, candidate));
    const touchDistance = (first, second) => Math.hypot(
        second.clientX - first.clientX,
        second.clientY - first.clientY
    );
    const touchCenter = (first, second) => ({
        x: (first.clientX + second.clientX) / 2,
        y: (first.clientY + second.clientY) / 2
    });
    const boundPan = () => {
        const maxX = baseWidth * (scale - minimumScale) / 2;
        const maxY = baseHeight * (scale - minimumScale) / 2;
        panX = Math.min(maxX, Math.max(-maxX, panX));
        panY = Math.min(maxY, Math.max(-maxY, panY));
    };
    const applyTransform = () => {
        if (!zoomTarget) return;
        zoomTarget.style.transformOrigin = "center center";
        zoomTarget.style.willChange = scale > minimumScale ? "transform" : "";
        zoomTarget.style.transform = scale > minimumScale
            ? `translate(${panX}px, ${panY}px) scale(${scale})`
            : "";
    };
    const resetZoom = () => {
        scale = minimumScale;
        panX = 0;
        panY = 0;
        applyTransform();
        zoomTarget = null;
        baseWidth = 0;
        baseHeight = 0;
        zoomGestureActive = false;
    };
    const report = (image, gesture) => {
        if (!image) return;
        window[stateKey] = JSON.stringify({
            gesture,
            href: image.getAttribute("src") || "",
            src: image.currentSrc || image.src || "",
            base: document.baseURI || ""
        });
    };
    const cancelLongPress = () => {
        if (longPressTimer !== null) clearTimeout(longPressTimer);
        longPressTimer = null;
        longPressTarget = null;
        activePointerId = null;
    };
    document.addEventListener("pointerdown", (event) => {
        const image = imageFor(event);
        if (!image) return;
        if (activePointerId !== null || !event.isPrimary) {
            cancelLongPress();
            return;
        }
        activePointerId = event.pointerId;
        startX = event.clientX;
        startY = event.clientY;
        longPressTarget = image;
        longPressTimer = setTimeout(() => {
            suppressNextClick = true;
            report(longPressTarget, "long_press");
            longPressTimer = null;
        }, 550);
    }, true);
    document.addEventListener("pointermove", (event) => {
        if (event.pointerId !== activePointerId) return;
        if (Math.hypot(event.clientX - startX, event.clientY - startY) > movementLimit) {
            cancelLongPress();
        }
    }, true);
    document.addEventListener("pointerup", (event) => {
        if (event.pointerId === activePointerId) cancelLongPress();
    }, true);
    document.addEventListener("pointercancel", cancelLongPress, true);
    document.addEventListener("scroll", cancelLongPress, true);
    document.addEventListener("touchstart", (event) => {
        const target = imageOnlyTarget();
        if (!target) return;
        if (event.touches.length >= 2) {
            cancelLongPress();
            zoomTarget = target;
            const rect = target.getBoundingClientRect();
            if (baseWidth <= 0 || baseHeight <= 0) {
                baseWidth = rect.width / scale;
                baseHeight = rect.height / scale;
            }
            pinchStartDistance = touchDistance(event.touches[0], event.touches[1]);
            pinchStartScale = scale;
            const center = touchCenter(event.touches[0], event.touches[1]);
            pinchStartCenterX = center.x;
            pinchStartCenterY = center.y;
            zoomGestureActive = true;
            event.preventDefault();
            event.stopImmediatePropagation();
        } else if (event.touches.length === 1 && scale > minimumScale && zoomTarget === target) {
            cancelLongPress();
            panStartX = event.touches[0].clientX;
            panStartY = event.touches[0].clientY;
            panOriginX = panX;
            panOriginY = panY;
            zoomGestureActive = true;
            event.preventDefault();
            event.stopImmediatePropagation();
        }
    }, { capture: true, passive: false });
    document.addEventListener("touchmove", (event) => {
        if (!zoomGestureActive || !zoomTarget) return;
        cancelLongPress();
        if (event.touches.length >= 2) {
            const distance = touchDistance(event.touches[0], event.touches[1]);
            if (pinchStartDistance > 0) scale = clampScale(pinchStartScale * distance / pinchStartDistance);
            const center = touchCenter(event.touches[0], event.touches[1]);
            panX += center.x - pinchStartCenterX;
            panY += center.y - pinchStartCenterY;
            pinchStartCenterX = center.x;
            pinchStartCenterY = center.y;
        } else if (event.touches.length === 1 && scale > minimumScale) {
            panX = panOriginX + event.touches[0].clientX - panStartX;
            panY = panOriginY + event.touches[0].clientY - panStartY;
        }
        boundPan();
        applyTransform();
        event.preventDefault();
        event.stopImmediatePropagation();
    }, { capture: true, passive: false });
    document.addEventListener("touchend", (event) => {
        if (!zoomGestureActive) return;
        event.preventDefault();
        event.stopImmediatePropagation();
        if (event.touches.length >= 2) {
            pinchStartDistance = touchDistance(event.touches[0], event.touches[1]);
            pinchStartScale = scale;
            const center = touchCenter(event.touches[0], event.touches[1]);
            pinchStartCenterX = center.x;
            pinchStartCenterY = center.y;
        } else if (event.touches.length === 1 && scale > minimumScale) {
            panStartX = event.touches[0].clientX;
            panStartY = event.touches[0].clientY;
            panOriginX = panX;
            panOriginY = panY;
        } else {
            zoomGestureActive = false;
            if (scale <= minimumScale) resetZoom();
        }
    }, { capture: true, passive: false });
    document.addEventListener("touchcancel", () => {
        zoomGestureActive = false;
        if (scale <= minimumScale) resetZoom();
    }, { capture: true, passive: false });
    document.addEventListener("click", (event) => {
        if (!suppressNextClick || !imageFor(event)) return;
        suppressNextClick = false;
        event.preventDefault();
        event.stopImmediatePropagation();
    }, true);
    window.__bookorbitImageZoomTestApi = {
        isImageOnlyDocument,
        clampScale,
        currentScale: () => scale,
        resetZoom
    };
    if (window.addEventListener) window.addEventListener("pagehide", resetZoom, true);
    return null;
})();
