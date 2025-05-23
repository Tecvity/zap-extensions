/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2025 The ZAP Development Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.addon.authhelper.client;

import org.zaproxy.addon.authhelper.AuthUtils;
import org.zaproxy.addon.authhelper.ClientScriptBasedAuthenticationMethodType;
import org.zaproxy.addon.authhelper.internal.AuthenticationBrowserHook;
import org.zaproxy.addon.client.spider.AuthenticationHandler;
import org.zaproxy.zap.extension.selenium.BrowserHook;
import org.zaproxy.zap.extension.selenium.ExtensionSelenium;
import org.zaproxy.zap.model.Context;
import org.zaproxy.zap.users.User;

public class ClientScriptBasedAuthHandler implements AuthenticationHandler {

    private BrowserHook browserHook;

    @Override
    public void enableAuthentication(User user) {
        Context context = user.getContext();
        if (context.getAuthenticationMethod()
                instanceof
                ClientScriptBasedAuthenticationMethodType.ClientScriptBasedAuthenticationMethod) {

            if (browserHook != null) {
                throw new IllegalStateException("BrowserHook already enabled");
            }
            browserHook = new AuthenticationBrowserHook(context, user);

            AuthUtils.getExtension(ExtensionSelenium.class).registerBrowserHook(browserHook);
        }
    }

    @Override
    public void disableAuthentication(User user) {
        if (browserHook != null) {
            AuthUtils.getExtension(ExtensionSelenium.class).deregisterBrowserHook(browserHook);
            browserHook = null;
        }
    }
}
