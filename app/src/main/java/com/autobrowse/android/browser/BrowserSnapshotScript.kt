package com.autobrowse.android.browser

object BrowserSnapshotScript {
    const val INTERACTIVE_SNAPSHOT = """
        (function() {
            var refs = [];
            function isInteractive(el) {
                var tag = el.tagName.toLowerCase();
                if (['a','button','input','select','textarea','summary','label'].indexOf(tag) >= 0) return true;
                if (el.getAttribute('role') === 'button' || el.getAttribute('role') === 'link') return true;
                if (el.onclick || el.getAttribute('onclick')) return true;
                if (el.tabIndex >= 0 && el.offsetParent !== null) return true;
                return false;
            }
            function walk(el, depth) {
                if (!el || depth > 10) return;
                try {
                    var rect = el.getBoundingClientRect();
                    if (rect.width < 2 || rect.height < 2) return;
                    var style = window.getComputedStyle(el);
                    if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return;
                    if (isInteractive(el)) {
                        var ref = '@e' + refs.length;
                        el.setAttribute('data-autobrowse-ref', ref);
                        refs.push({
                            ref: ref,
                            tag: el.tagName.toLowerCase(),
                            text: (el.innerText || el.value || el.placeholder || el.getAttribute('aria-label') || '').trim().slice(0, 120),
                            type: el.type || '',
                            href: el.href || '',
                            selector: ref
                        });
                    }
                    for (var i = 0; i < el.children.length; i++) walk(el.children[i], depth + 1);
                } catch (e) {}
            }
            walk(document.body, 0);
            return JSON.stringify({
                url: location.href,
                title: document.title,
                refs: refs,
                text: document.body ? document.body.innerText.slice(0, 6000) : ''
            });
        })();
    """

    fun clickRefScript(ref: String): String = """
        (function() {
            var el = document.querySelector('[data-autobrowse-ref="$ref"]');
            if (!el) return 'not_found';
            el.scrollIntoView({block:'center'});
            el.click();
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
                if ('value' in el) el.value = '';
                if (el.isContentEditable) el.textContent = '';
                var text = '$escaped';
                if ('value' in el) {
                    el.value = text;
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                } else if (el.isContentEditable) {
                    el.textContent = text;
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                }
                return 'typed';
            })();
        """.trimIndent()
    }

    fun clickXYScript(x: Int, y: Int): String = """
        (function() {
            var el = document.elementFromPoint($x, $y);
            if (!el) return 'not_found';
            var rect = el.getBoundingClientRect();
            var evt = new MouseEvent('click', {
                bubbles: true, cancelable: true, view: window,
                clientX: $x, clientY: $y
            });
            el.dispatchEvent(evt);
            return 'clicked_xy at ($x,$y) on ' + el.tagName.toLowerCase();
        })();
    """.trimIndent()

    fun scrollScript(direction: String, amount: Int): String {
        val delta = if (direction == "up") -amount else amount
        return """
            (function() {
                window.scrollBy({ top: $delta, behavior: 'smooth' });
                return 'scrolled_${direction}_$amount';
            })();
        """.trimIndent()
    }

    fun pressKeyScript(key: String): String {
        val escaped = key.replace("'", "\\'")
        return """
            (function() {
                var key = '$escaped';
                var code = key.length === 1 ? 'Key' + key.toUpperCase() : key;
                document.activeElement.dispatchEvent(new KeyboardEvent('keydown', { key: key, code: code, bubbles: true }));
                document.activeElement.dispatchEvent(new KeyboardEvent('keyup', { key: key, code: code, bubbles: true }));
                if (key === 'Enter') {
                    var form = document.activeElement.form;
                    if (form) form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
                }
                return 'pressed_' + key;
            })();
        """.trimIndent()
    }
}