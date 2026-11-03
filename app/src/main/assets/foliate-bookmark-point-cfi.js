/*
 * Point-based EPUB CFI generation adapted from foliate-js/epubcfi.js.
 * Source: https://github.com/johnfactotum/foliate-js/blob/main/epubcfi.js
 *
 * MIT License
 * Copyright (c) 2022 John Factotum
 */
(() => {
    const escapeCFI = value => value.replace(/[\^[\](),;=]/g, '^$&');
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

    const nodeToParts = (node, offset) => {
        const parentNode = node?.parentNode;
        if (!parentNode) return [];
        const indexed = indexChildNodes(parentNode);
        const index = indexed.findIndex(item =>
            Array.isArray(item) ? item.some(part => part === node) : item === node);
        const chunk = indexed[index];
        if (Array.isArray(chunk)) {
            let sum = 0;
            for (const part of chunk) {
                if (part === node) {
                    sum += offset;
                    break;
                }
                sum += part.nodeValue.length;
            }
            offset = sum;
        }
        const part = { id: node.id, index, offset };
        const root = node.ownerDocument?.documentElement;
        return (parentNode !== root ? nodeToParts(parentNode, null).concat(part) : [part])
            .filter(value => value.index !== -1);
    };

    const partToString = ({ index, id, offset }) => `/${index}` +
        (id ? `[${escapeCFI(id)}]` : '') +
        (offset != null && index % 2 ? `:${offset}` : '');

    const x = Math.floor(window.innerWidth / 2);
    const y = Math.floor(window.innerHeight / 2);
    const range = document.caretRangeFromPoint?.(x, y);
    const position = !range ? document.caretPositionFromPoint?.(x, y) : null;
    let node = range?.startContainer || position?.offsetNode || null;
    let offset = range?.startOffset ?? position?.offset ?? null;
    if (!node) {
        node = document.elementFromPoint(x, y);
        offset = null;
    }
    if (!node) return null;

    const path = nodeToParts(node, offset).map(partToString).join('');
    return path.startsWith('/') ? `epubcfi(${path})` : null;
})();
