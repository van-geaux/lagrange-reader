/*
 * CFI step indexing adapted from foliate-js/epubcfi.js.
 * Source: https://github.com/johnfactotum/foliate-js/blob/main/epubcfi.js
 *
 * MIT License
 * Copyright (c) 2022 John Factotum
 */
(() => {
    const getChildNodes = node => Array.from(node.childNodes)
        .filter(child => child.nodeType === Node.TEXT_NODE ||
            child.nodeType === Node.CDATA_SECTION_NODE ||
            child.nodeType === Node.ELEMENT_NODE);

    const indexChildNodes = node => {
        const nodes = getChildNodes(node).reduce((result, child) => {
            const last = result[result.length - 1];
            const isText = child.nodeType === Node.TEXT_NODE ||
                child.nodeType === Node.CDATA_SECTION_NODE;
            const lastIsText = last && !Array.isArray(last) &&
                (last.nodeType === Node.TEXT_NODE || last.nodeType === Node.CDATA_SECTION_NODE);
            if (!last) result.push(child);
            else if (isText) {
                if (Array.isArray(last)) last.push(child);
                else if (lastIsText) result[result.length - 1] = [last, child];
                else result.push(child);
            } else {
                if (last.nodeType === Node.ELEMENT_NODE) result.push(null, child);
                else result.push(child);
            }
            return result;
        }, []);
        if (nodes[0]?.nodeType === Node.ELEMENT_NODE) nodes.unshift('first');
        if (nodes[nodes.length - 1]?.nodeType === Node.ELEMENT_NODE) nodes.push('last');
        nodes.unshift('before');
        nodes.push('after');
        return nodes;
    };

    const topLevelPositions = (value, token) => {
        const positions = [];
        let bracketDepth = 0;
        for (let index = 0; index < value.length; index++) {
            const character = value[index];
            if (character === '^') {
                index++;
            } else if (character === '[') {
                bracketDepth++;
            } else if (character === ']') {
                bracketDepth = Math.max(0, bracketDepth - 1);
            } else if (character === token && bracketDepth === 0) {
                positions.push(index);
            }
        }
        return positions;
    };

    const parsePointCfi = cfi => {
        if (typeof cfi !== 'string' || !cfi.startsWith('epubcfi(') || !cfi.endsWith(')')) return null;
        const body = cfi.slice(8, -1);
        const indirections = topLevelPositions(body, '!');
        if (indirections.length !== 1) return null;
        const indirection = indirections[0];
        const rangeParts = topLevelPositions(body.slice(indirection + 1), ',');
        let path = body.slice(indirection + 1);
        if (rangeParts.length > 0) {
            if (rangeParts.length !== 2) return null;
            const content = body.slice(indirection + 1);
            const commonPath = content.slice(0, rangeParts[0]);
            const startPath = content.slice(rangeParts[0] + 1, rangeParts[1]);
            const endPath = content.slice(rangeParts[1] + 1);
            if (!commonPath.startsWith('/') || !startPath || !endPath ||
                !(startPath.startsWith('/') || startPath.startsWith(':'))) return null;
            path = commonPath + startPath;
        }
        const steps = [];
        let cursor = 0;
        while (cursor < path.length) {
            if (path[cursor++] !== '/') return null;
            const numberStart = cursor;
            while (cursor < path.length && /[0-9]/.test(path[cursor])) cursor++;
            if (numberStart === cursor) return null;
            const step = { index: Number(path.slice(numberStart, cursor)), id: null, offset: null };
            if (path[cursor] === '[') {
                cursor++;
                let assertion = '';
                let closed = false;
                while (cursor < path.length) {
                    const character = path[cursor++];
                    if (character === '^') {
                        if (cursor >= path.length) return null;
                        assertion += path[cursor++];
                    } else if (character === ']') {
                        closed = true;
                        break;
                    } else {
                        assertion += character;
                    }
                }
                if (!closed) return null;
                step.id = assertion;
            }
            if (path[cursor] === ':') {
                cursor++;
                const offsetStart = cursor;
                while (cursor < path.length && /[0-9]/.test(path[cursor])) cursor++;
                if (offsetStart === cursor) return null;
                step.offset = Number(path.slice(offsetStart, cursor));
                if (path[cursor] === '[') {
                    cursor++;
                    let closed = false;
                    while (cursor < path.length) {
                        if (path[cursor++] === '^') cursor++;
                        else if (path[cursor - 1] === ']') {
                            closed = true;
                            break;
                        }
                    }
                    if (!closed) return null;
                }
            }
            if (path[cursor] === ';') {
                while (cursor < path.length && path[cursor] !== '/') cursor++;
            }
            if (cursor < path.length && path[cursor] !== '/') return null;
            steps.push(step);
        }
        return steps.length ? steps : null;
    };

    const selectorForElement = element => {
        if (!element || !document.body.contains(element)) return null;
        const parts = [];
        let current = element;
        while (current && current !== document.body) {
            const name = current.localName;
            if (!name) return null;
            let siblingIndex = 1;
            for (let sibling = current.previousElementSibling; sibling; sibling = sibling.previousElementSibling) {
                if (sibling.localName === name) siblingIndex++;
            }
            parts.unshift(`${name}:nth-of-type(${siblingIndex})`);
            current = current.parentElement;
        }
        if (current !== document.body) return null;
        return ['body', ...parts].join(' > ');
    };

    const nodeAtOffset = (node, offset) => {
        if (!Array.isArray(node)) {
            if (node?.nodeType !== Node.TEXT_NODE && node?.nodeType !== Node.CDATA_SECTION_NODE) return null;
            return offset <= node.nodeValue.length ? { node, offset } : null;
        }
        let remaining = offset;
        for (const part of node) {
            const length = part.nodeValue.length;
            if (remaining <= length) return { node: part, offset: remaining };
            remaining -= length;
        }
        return null;
    };

    const progressionAt = range => {
        const scroller = document.scrollingElement || document.documentElement;
        const rect = range.getBoundingClientRect();
        const scrollMode = document.documentElement.style.getPropertyValue('--USER__view').trim() === 'readium-scroll-on' ||
            document.documentElement.style.getPropertyValue('--USER__scroll').trim() === 'readium-scroll-on';
        if (scrollMode) {
            const extent = scroller.scrollHeight;
            return {
                progression: extent > 0 ? Math.max(0, Math.min(1, (rect.top + window.scrollY) / extent)) : 0,
                progressionTolerance: 0.01
            };
        }
        const extent = scroller.scrollWidth;
        if (extent <= 0) return { progression: 0, progressionTolerance: 0.02 };
        const isRtl = document.body.dir.toLowerCase() === 'rtl';
        const viewportWidth = window.Android?.getViewportWidth
            ? window.Android.getViewportWidth() / window.devicePixelRatio
            : window.innerWidth;
        const offset = rect.left + window.scrollX;
        const value = offset + (isRtl ? -1 : 1);
        const snappedOffset = value - (value % viewportWidth);
        const x = snappedOffset + (isRtl ? viewportWidth : 0);
        const progression = x / extent;
        return {
            progression: Math.max(0, Math.min(1, isRtl ? 1 - progression : progression)),
            progressionTolerance: Math.min(0.25, Math.max(0.02, viewportWidth / extent + 0.01))
        };
    };

    const isUniqueQuote = quote => {
        if (!quote) return false;
        const text = document.body.textContent || '';
        const first = text.indexOf(quote);
        return first >= 0 && text.indexOf(quote, first + 1) < 0;
    };

    const resolve = cfi => {
        const steps = parsePointCfi(cfi);
        if (!steps) return null;
        let node = document.documentElement;
        let point = null;
        for (let index = 0; index < steps.length; index++) {
            const step = steps[index];
            const child = indexChildNodes(node)[step.index];
            if (!child || typeof child === 'string') return null;
            if (step.id && (Array.isArray(child) || child.id !== step.id)) return null;
            if (step.offset != null) {
                if (index !== steps.length - 1) return null;
                point = nodeAtOffset(child, step.offset);
                if (!point) return null;
                node = point.node;
            } else {
                node = Array.isArray(child) ? child[0] : child;
            }
        }
        if (!document.body.contains(node)) return null;
        if (!point) {
            const element = node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement;
            if (!element) return null;
            const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
            const firstText = walker.nextNode();
            if (firstText) point = { node: firstText, offset: 0 };
            else {
                const range = document.createRange();
                range.selectNodeContents(element);
                range.collapse(true);
                const selector = selectorForElement(element);
                return selector ? {
                    before: '', highlight: '', after: '',
                    cssSelector: selector,
                    ...progressionAt(range)
                } : null;
            }
        }
        const beforeRange = document.createRange();
        beforeRange.selectNodeContents(document.body);
        beforeRange.setEnd(point.node, point.offset);
        const afterRange = document.createRange();
        afterRange.selectNodeContents(document.body);
        afterRange.setStart(point.node, point.offset);
        const after = afterRange.toString();
        const range = document.createRange();
        range.setStart(point.node, point.offset);
        range.collapse(true);
        const targetElement = point.node.nodeType === Node.ELEMENT_NODE
            ? point.node
            : point.node.parentElement;
        const cssSelector = selectorForElement(targetElement);
        const highlight = after.slice(0, 64);
        const uniqueQuote = isUniqueQuote(highlight);
        return {
            before: uniqueQuote ? beforeRange.toString().slice(-64) : '',
            highlight: uniqueQuote ? highlight : '',
            after: uniqueQuote ? after.slice(64, 128) : '',
            cssSelector,
            ...progressionAt(range)
        };
    };

    window.bookorbitResolveBookmarkCfi = resolve;
})();
