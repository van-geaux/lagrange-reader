/*
 * Selection-range EPUB CFI generation adapted from foliate-js/epubcfi.js.
 * Source: https://github.com/johnfactotum/foliate-js/blob/main/epubcfi.js
 *
 * MIT License
 * Copyright (c) 2022 John Factotum
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
(function () {
    const escapeCFI = value => value.replace(/[\^[\](),;=]/g, '^$&');
    const wrap = value => `epubcfi(${value})`;

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
        const parentNode = node.parentNode;
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
        const root = node.ownerDocument.documentElement;
        return (parentNode !== root ? nodeToParts(parentNode, null).concat(part) : [part])
            .filter(value => value.index !== -1);
    };

    const partToString = ({ index, id, offset }) => `/${index}` +
        (id ? `[${escapeCFI(id)}]` : '') +
        (offset != null && index % 2 ? `:${offset}` : '');

    const commonParentLength = (start, end) => {
        const length = Math.min(start.length, end.length);
        let index = 0;
        while (index < length && start[index].index === end[index].index &&
            start[index].offset == null && end[index].offset == null) index += 1;
        return index;
    };

    const fromRange = range => {
        const start = nodeToParts(range.startContainer, range.startOffset);
        const end = nodeToParts(range.endContainer, range.endOffset);
        if (range.collapsed) return null;
        const parentLength = commonParentLength(start, end);
        const parent = start.slice(0, parentLength).map(partToString).join('');
        const startPath = start.slice(parentLength).map(partToString).join('');
        const endPath = end.slice(parentLength).map(partToString).join('');
        if (!parent || !startPath || !endPath) return null;
        return wrap(`${parent},${startPath},${endPath}`);
    };

    const selection = window.getSelection();
    if (!selection || selection.isCollapsed || selection.rangeCount < 1) return null;
    return fromRange(selection.getRangeAt(0));
})();
