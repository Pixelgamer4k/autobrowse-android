package com.autobrowse.android.browser

object CaptchaTokenInjector {
    fun injectRecaptchaV2(token: String): String {
        val escaped = escapeJs(token)
        return """
            (function() {
                var token = '$escaped';
                var areas = document.querySelectorAll('#g-recaptcha-response, [name="g-recaptcha-response"]');
                areas.forEach(function(el) { el.value = token; el.innerHTML = token; });
                var widget = document.querySelector('.g-recaptcha, [data-sitekey]');
                var cbName = widget ? widget.getAttribute('data-callback') : null;
                if (cbName && typeof window[cbName] === 'function') {
                    try { window[cbName](token); return 'injected_callback'; } catch (e) {}
                }
                if (typeof grecaptcha !== 'undefined' && grecaptcha.getResponse) {
                    try {
                        var clients = document.querySelectorAll('[data-sitekey]');
                        for (var i = 0; i < clients.length; i++) {
                            var id = clients[i].getAttribute('data-widget-id');
                            if (id && grecaptcha.execute) { grecaptcha.execute(id); }
                        }
                    } catch (e) {}
                }
                return areas.length ? 'injected_textarea' : 'no_recaptcha_field';
            })();
        """.trimIndent()
    }

    fun injectHcaptcha(token: String): String {
        val escaped = escapeJs(token)
        return """
            (function() {
                var token = '$escaped';
                document.querySelectorAll('[name="h-captcha-response"], [name="g-recaptcha-response"]').forEach(function(el) {
                    el.value = token;
                });
                var widget = document.querySelector('.h-captcha, [data-hcaptcha-widget-id]');
                var cbName = widget ? widget.getAttribute('data-callback') : null;
                if (cbName && typeof window[cbName] === 'function') {
                    try { window[cbName](token); return 'injected_hcaptcha_callback'; } catch (e) {}
                }
                return 'injected_hcaptcha';
            })();
        """.trimIndent()
    }

    fun injectTurnstile(token: String): String {
        val escaped = escapeJs(token)
        return """
            (function() {
                var token = '$escaped';
                document.querySelectorAll('[name="cf-turnstile-response"]').forEach(function(el) {
                    el.value = token;
                });
                var widget = document.querySelector('.cf-turnstile, [data-turnstile-widget]');
                var cbName = widget ? widget.getAttribute('data-callback') : null;
                if (cbName && typeof window[cbName] === 'function') {
                    try { window[cbName](token); return 'injected_turnstile_callback'; } catch (e) {}
                }
                return 'injected_turnstile';
            })();
        """.trimIndent()
    }

    private fun escapeJs(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")
}