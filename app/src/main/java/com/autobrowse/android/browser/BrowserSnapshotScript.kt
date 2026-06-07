package com.autobrowse.android.browser

object BrowserSnapshotScript {
    const val INTERACTIVE_SNAPSHOT = """
        (function() {
            var refs = [];
            function label(el) {
                return (el.getAttribute('aria-label') || el.placeholder || el.name || el.id || el.title || '').trim();
            }
            function isInteractive(el) {
                var tag = el.tagName.toLowerCase();
                if (['a','button','input','select','textarea','summary'].indexOf(tag) >= 0) return true;
                if (el.isContentEditable) return true;
                var role = el.getAttribute('role') || '';
                if (['button','link','searchbox','combobox','textbox','menuitem','tab'].indexOf(role) >= 0) return true;
                if (el.onclick || el.getAttribute('onclick')) return true;
                if (tag === 'input' && (el.type === 'search' || el.type === 'text')) return true;
                if (label(el).toLowerCase().indexOf('search') >= 0) return true;
                return false;
            }
            function walk(el, depth) {
                if (!el || depth > 12) return;
                try {
                    var rect = el.getBoundingClientRect();
                    if (rect.width < 2 || rect.height < 2) return;
                    var style = window.getComputedStyle(el);
                    if (style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity) < 0.1) return;
                    if (isInteractive(el)) {
                        var ref = '@e' + refs.length;
                        el.setAttribute('data-autobrowse-ref', ref);
                        refs.push({
                            ref: ref,
                            tag: el.tagName.toLowerCase(),
                            text: (el.innerText || el.value || '').trim().slice(0, 120),
                            label: label(el).slice(0, 80),
                            type: el.type || '',
                            role: el.getAttribute('role') || '',
                            contenteditable: el.isContentEditable ? 'true' : 'false',
                            href: el.href || ''
                        });
                    }
                    for (var i = 0; i < el.children.length; i++) walk(el.children[i], depth + 1);
                } catch (e) {}
            }
            walk(document.body, 0);
            refs.sort(function(a, b) {
                var aSearch = (a.role === 'searchbox' || a.type === 'search' || a.label.toLowerCase().indexOf('search') >= 0) ? 0 : 1;
                var bSearch = (b.role === 'searchbox' || b.type === 'search' || b.label.toLowerCase().indexOf('search') >= 0) ? 0 : 1;
                return aSearch - bSearch;
            });
            return JSON.stringify({
                url: location.href,
                title: document.title,
                readyState: document.readyState,
                refs: refs,
                text: document.body ? document.body.innerText.slice(0, 8000) : ''
            });
        })();
    """

    fun clickRefScript(ref: String): String = """
        (function() {
            var el = document.querySelector('[data-autobrowse-ref="$ref"]');
            if (!el) return 'not_found';
            el.scrollIntoView({block:'center', inline:'center'});
            var rect = el.getBoundingClientRect();
            var x = rect.left + rect.width / 2;
            var y = rect.top + rect.height / 2;
            ['mousedown','mouseup','click'].forEach(function(type) {
                el.dispatchEvent(new MouseEvent(type, {bubbles:true, cancelable:true, view:window, clientX:x, clientY:y}));
            });
            if (typeof el.click === 'function') el.click();
            return 'clicked $ref';
        })();
    """.trimIndent()

    fun typeRefScript(ref: String, text: String): String {
        val escaped = text.replace("\\", "\\\\").replace("'", "\\'")
        return """
            (function() {
                var el = document.querySelector('[data-autobrowse-ref="$ref"]');
                if (!el) return 'not_found';
                el.scrollIntoView({block:'center'});
                el.focus();
                var text = '$escaped';
                if (el.isContentEditable) {
                    el.textContent = '';
                    el.textContent = text;
                    el.dispatchEvent(new InputEvent('input', {bubbles:true, data:text, inputType:'insertText'}));
                } else if ('value' in el) {
                    el.value = '';
                    var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
                    if (setter && setter.set) setter.set.call(el, text);
                    else el.value = text;
                    el.dispatchEvent(new InputEvent('input', {bubbles:true, data:text, inputType:'insertText'}));
                    el.dispatchEvent(new Event('change', {bubbles:true}));
                }
                return 'typed';
            })();
        """.trimIndent()
    }

    fun clickXYScript(x: Int, y: Int): String = """
        (function() {
            var el = document.elementFromPoint($x, $y);
            if (!el) return 'not_found';
            ['mousedown','mouseup','click'].forEach(function(type) {
                el.dispatchEvent(new MouseEvent(type, {bubbles:true, cancelable:true, view:window, clientX:$x, clientY:$y}));
            });
            return 'clicked_xy at ($x,$y) on ' + el.tagName.toLowerCase();
        })();
    """.trimIndent()

    fun scrollScript(direction: String, amount: Int): String {
        val delta = if (direction == "up") -amount else amount
        return """
            (function() {
                window.scrollBy({ top: $delta, behavior: 'instant' });
                return 'scrolled_${direction}_$amount';
            })();
        """.trimIndent()
    }

    fun pressKeyScript(key: String): String {
        val escaped = key.replace("'", "\\'")
        return """
            (function() {
                var key = '$escaped';
                var target = document.activeElement || document.body;
                var code = key === 'Enter' ? 'Enter' : (key.length === 1 ? 'Key' + key.toUpperCase() : key);
                ['keydown','keypress','keyup'].forEach(function(type) {
                    target.dispatchEvent(new KeyboardEvent(type, {key:key, code:code, bubbles:true, cancelable:true}));
                });
                if (key === 'Enter') {
                    var form = target.form;
                    if (form) {
                        if (form.requestSubmit) form.requestSubmit();
                        else form.dispatchEvent(new Event('submit', {bubbles:true, cancelable:true}));
                    }
                }
                return 'pressed_' + key;
            })();
        """.trimIndent()
    }
}