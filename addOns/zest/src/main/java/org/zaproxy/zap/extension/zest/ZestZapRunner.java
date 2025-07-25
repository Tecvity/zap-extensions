/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2013 The ZAP Development Team
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
package org.zaproxy.zap.extension.zest;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.script.ScriptEngine;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.parosproxy.paros.control.Control;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.core.scanner.HostProcess;
import org.parosproxy.paros.core.scanner.Scanner;
import org.parosproxy.paros.core.scanner.ScannerListener;
import org.parosproxy.paros.core.scanner.ScannerParam;
import org.parosproxy.paros.model.HistoryReference;
import org.parosproxy.paros.model.Model;
import org.parosproxy.paros.model.SiteNode;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.network.HttpSender;
import org.parosproxy.paros.view.View;
import org.zaproxy.addon.network.ExtensionNetwork;
import org.zaproxy.addon.network.server.ServerInfo;
import org.zaproxy.zap.extension.alert.ExtensionAlert;
import org.zaproxy.zap.extension.ascan.ExtensionActiveScan;
import org.zaproxy.zap.extension.ascan.ScanPolicy;
import org.zaproxy.zap.extension.ruleconfig.ExtensionRuleConfig;
import org.zaproxy.zap.extension.ruleconfig.RuleConfigParam;
import org.zaproxy.zap.extension.script.ExtensionScript;
import org.zaproxy.zap.extension.script.ScriptUI;
import org.zaproxy.zap.extension.script.ScriptVars;
import org.zaproxy.zap.extension.selenium.Browser;
import org.zaproxy.zap.extension.selenium.ExtensionSelenium;
import org.zaproxy.zest.core.v1.ZestAction;
import org.zaproxy.zest.core.v1.ZestActionFail;
import org.zaproxy.zest.core.v1.ZestActionFailException;
import org.zaproxy.zest.core.v1.ZestActionIntercept;
import org.zaproxy.zest.core.v1.ZestActionScan;
import org.zaproxy.zest.core.v1.ZestAssertFailException;
import org.zaproxy.zest.core.v1.ZestAssertion;
import org.zaproxy.zest.core.v1.ZestAssignFailException;
import org.zaproxy.zest.core.v1.ZestAssignment;
import org.zaproxy.zest.core.v1.ZestClient;
import org.zaproxy.zest.core.v1.ZestClientFailException;
import org.zaproxy.zest.core.v1.ZestClientLaunch;
import org.zaproxy.zest.core.v1.ZestInvalidCommonTestException;
import org.zaproxy.zest.core.v1.ZestRequest;
import org.zaproxy.zest.core.v1.ZestResponse;
import org.zaproxy.zest.core.v1.ZestScript;
import org.zaproxy.zest.core.v1.ZestStatement;
import org.zaproxy.zest.impl.ZestBasicRunner;

public class ZestZapRunner extends ZestBasicRunner implements ScannerListener {

    private static final Logger LOGGER = LogManager.getLogger(ZestZapRunner.class);

    private static final int ZEST_HISTORY_REFERENCE_TYPE = HistoryReference.TYPE_ZEST_SCRIPT;
    private static final int FAIL_ACTION_PLUGIN_ID = 50004;

    private static Field fieldOutputWriter;

    private ExtensionZest extension;
    private final ExtensionNetwork extensionNetwork;
    private ZestScriptWrapper wrapper = null;
    private HttpMessage target = null;
    private ZestResultWrapper lastResult = null;
    private HistoryReference lastHref = null;

    private boolean pause = false;
    private boolean isStop = false;

    private boolean scanning = false;

    private List<Alert> alerts = new ArrayList<>();

    private final ExtensionScript extensionScript;
    private ScriptUI scriptUI;

    public ZestZapRunner(
            ExtensionZest extension, ExtensionNetwork extensionNetwork, ZestScriptWrapper wrapper) {
        super(Default.TIMEOUT_IN_SECONDS, true);
        LOGGER.debug("Constructor");
        this.extension = extension;
        this.extensionNetwork = extensionNetwork;
        this.wrapper = wrapper;
        extensionScript = extension.getExtScript();
        this.scriptUI = extension.getExtScript().getScriptUI();
        this.setScriptEngineFactory(extension.getZestScriptEngineFactory());

        this.setStopOnAssertFail(false);
        this.setStopOnTestFail(false);

        // Always proxy via ZAP
        ServerInfo serverInfo = extensionNetwork.getMainProxyServerInfo();
        this.setProxy(serverInfo.getAddress(), serverInfo.getPort());
    }

    @Override
    public ScriptEngine getScriptEngine(String extension) {
        String name = extensionScript.getEngineNameForExtension(extension);
        if (name == null) {
            return null;
        }

        try {
            return extensionScript.getEngineWrapper(name).getEngine();
        } catch (InvalidParameterException e) {
            LOGGER.debug("Failed to get the engine.", e);
        }
        return null;
    }

    protected ExtensionNetwork getExtensionNetwork() {
        return extensionNetwork;
    }

    @Override
    public String run(ZestScript script, Map<String, String> params)
            throws ZestAssertFailException,
                    ZestActionFailException,
                    IOException,
                    ZestInvalidCommonTestException,
                    ZestAssignFailException,
                    ZestClientFailException {
        LOGGER.debug("Run script {}", script.getTitle());
        // Check for any missing parameters
        boolean missingParams = false;
        for (String[] vars : script.getParameters().getVariables()) {
            if (vars[1].length() == 0 && params.get(vars[0]) == null) {
                missingParams = true;
            }
        }
        if (missingParams) {
            // Prompt for them
            params = extension.getDialogManager().showRunScriptDialog(this, script, params);
            return "";
        } else {
            this.target = null;
            if (wrapper.getWriter() != null) {
                super.setOutputWriter(wrapper.getWriter());
            } else if (scriptUI != null && !hasOutputWriter()) {
                super.setOutputWriter(scriptUI.getOutputWriter());
            }
            this.setDebug(this.wrapper.isDebug());

            return super.run(script, params);
        }
    }

    private boolean hasOutputWriter() {
        try {
            if (fieldOutputWriter == null) {
                fieldOutputWriter = ZestBasicRunner.class.getDeclaredField("outputWriter");
                fieldOutputWriter.setAccessible(true);
            }
            return fieldOutputWriter.get(this) != null;
        } catch (IllegalAccessException | NoSuchFieldException e) {
            return false;
        }
    }

    @Override
    public String run(ZestScript script, ZestRequest target, Map<String, String> params)
            throws ZestAssertFailException,
                    ZestActionFailException,
                    IOException,
                    ZestInvalidCommonTestException,
                    ZestAssignFailException,
                    ZestClientFailException {
        LOGGER.debug("Run script {}", script.getTitle());
        if (wrapper.getWriter() != null) {
            super.setOutputWriter(wrapper.getWriter());
        } else if (scriptUI != null && !hasOutputWriter()) {
            super.setOutputWriter(scriptUI.getOutputWriter());
        }
        this.setDebug(this.wrapper.isDebug());
        String result = super.run(script, target, params);
        return result;
    }

    public void stop() {
        isStop = true;
    }

    private void notifyResponse(ZestResultWrapper href) {
        this.lastHref = href;
        if (View.isInitialised()) {
            if (scriptUI != null && scriptUI.isScriptDisplayed(wrapper)) {
                // Add to the Zest results tab
                this.extension.addResultToList(href);
            }
            // Add to history tab
            /* TODO wont work until ExtensionHistory changed to display non MANUAL requests
            ExtensionHistory extHist = (ExtensionHistory) Control.getSingleton().getExtensionLoader().getExtension(ExtensionHistory.NAME);
            if (extHist != null) {
            	extHist.addHistory(href);
            }
            */

        } else {
            // TODO i18n for cmdline??
            try {
                System.out.println(
                        "Response: "
                                + href.getURI()
                                + " passed = "
                                + href.isPassed()
                                + " code="
                                + href.getStatusCode());
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }
    }

    private void notifyActionFailed(ZestActionFailException e) {
        LOGGER.debug("notifyActionFailed", e);
        if (e.getAction() instanceof ZestActionFail) {
            int risk = Alert.RISK_LOW;
            ZestActionFail zaf = (ZestActionFail) e.getAction();
            if (ZestActionFail.Priority.INFO.name().equals(zaf.getPriority())) {
                risk = Alert.RISK_INFO;
            } else if (ZestActionFail.Priority.LOW.name().equals(zaf.getPriority())) {
                risk = Alert.RISK_LOW;
            } else if (ZestActionFail.Priority.MEDIUM.name().equals(zaf.getPriority())) {
                risk = Alert.RISK_MEDIUM;
            } else if (ZestActionFail.Priority.HIGH.name().equals(zaf.getPriority())) {
                risk = Alert.RISK_HIGH;
            }
            Alert.Builder alertBuilder =
                    Alert.builder()
                            .setPluginId(FAIL_ACTION_PLUGIN_ID)
                            .setRisk(risk)
                            .setConfidence(Alert.CONFIDENCE_MEDIUM)
                            .setName(e.getMessage())
                            .setHistoryRef(lastHref);
            this.alertFound(alertBuilder.build());
        }

        if (View.isInitialised()) {
            if (scriptUI != null && scriptUI.isScriptDisplayed(wrapper)) {
                if (!hasSameScriptType(wrapper.getZestScript(), ZestScript.Type.Passive)) {
                    // Dont try to update passive scripts - they cant make requests so the
                    // last request wont be in the results list
                    extension.failLastResult(e);
                }
            }
        } else {
            // TODO i18n for cmdline??
            // TODO check type first? toUiFailureString as above?
            System.out.println("Action: failed: " + e.getMessage());
        }
    }

    private static boolean hasSameScriptType(ZestScript script, ZestScript.Type type) {
        return type.name().equalsIgnoreCase(script.getType());
    }

    private void notifyAssignFailed(ZestAssignFailException e) {
        LOGGER.debug("notifyAssignFailed", e);
        if (View.isInitialised()) {
            if (scriptUI != null && scriptUI.isScriptDisplayed(wrapper)) {
                if (!hasSameScriptType(wrapper.getZestScript(), ZestScript.Type.Passive)) {
                    // Dont try to update passive scripts - they cant make requests so the
                    // last request wont be in the results list
                    extension.failLastResult(e);
                }
            }
        } else {
            // TODO i18n for cmdline??
            // TODO check type first? toUiFailureString as above?
            System.out.println("Assign: failed: " + e.getMessage());
        }
    }

    @Override
    public ZestResponse runStatement(
            ZestScript script, ZestStatement stmt, ZestResponse lastResponse)
            throws ZestAssertFailException,
                    ZestActionFailException,
                    ZestInvalidCommonTestException,
                    IOException,
                    ZestAssignFailException,
                    ZestClientFailException {
        LOGGER.debug("runStatement {}", stmt.getElementType());
        while (this.isPaused() && !this.isStop) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
        if (this.isStop) {
            return null;
        }
        return super.runStatement(script, stmt, lastResponse);
    }

    @Override
    public String handleAction(ZestScript script, ZestAction action, ZestResponse lastResponse)
            throws ZestActionFailException {
        LOGGER.debug("handleAction {}", action.getElementType());
        if (action instanceof ZestActionScan) {
            this.invokeScan(script, (ZestActionScan) action);
        } else if (action instanceof ZestActionIntercept) {
            // Use a script variable, which we check in ZestProxyRunner
            script.getParameters()
                    .setVariable(
                            ZestScriptWrapper.ZAP_BREAK_VARIABLE_NAME,
                            ZestScriptWrapper.ZAP_BREAK_VARIABLE_VALUE);
        } else {
            try {
                return super.handleAction(script, action, lastResponse);
            } catch (ZestActionFailException e) {
                notifyActionFailed(e);
            }
        }
        return null;
    }

    @Override
    public String handleAssignment(
            ZestScript script, ZestAssignment assign, ZestResponse lastResponse)
            throws ZestAssignFailException {
        LOGGER.debug("handleAssignment {}", assign.getElementType());
        try {
            return super.handleAssignment(script, assign, lastResponse);
        } catch (ZestAssignFailException e) {
            notifyAssignFailed(e);
        }
        return null;
    }

    @Override
    public void handleResponse(ZestRequest request, ZestResponse response)
            throws ZestAssertFailException {
        LOGGER.debug("handleResponse {}", request.getElementType());
        try {
            HttpMessage msg = ZestZapUtils.toHttpMessage(request, response);

            ZestResultWrapper zrw =
                    new ZestResultWrapper(
                            Model.getSingleton().getSession(),
                            ZEST_HISTORY_REFERENCE_TYPE,
                            msg,
                            request.getIndex());

            lastResult = zrw;

            if (request.getAssertions().isEmpty()) {
                zrw.setPassed(true);
            } else {
                for (ZestAssertion za : request.getAssertions()) {
                    if (za.isValid(this)) {
                        zrw.setPassed(true);
                    } else {
                        zrw.setPassed(false);
                        zrw.setMessage(ZestZapUtils.toUiFailureString(za, this));
                        break;
                    }
                }
            }
            this.notifyResponse(zrw);

        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public String handleClient(ZestScript script, ZestClient client)
            throws ZestClientFailException {
        try {
            if (client instanceof ZestClientLaunch clientLaunch) {
                String res = this.launchClient(clientLaunch);
                if (res != null) {
                    return res;
                }
            }
            return super.handleClient(script, client);
        } catch (ZestClientFailException e) {
            throw e;
        } catch (Exception e) {
            throw new ZestClientFailException(client, e);
        }
    }

    private String launchClient(ZestClientLaunch clientLaunch) {
        ExtensionSelenium extSel =
                Control.getSingleton().getExtensionLoader().getExtension(ExtensionSelenium.class);
        Browser browser = null;

        if ("Firefox".equalsIgnoreCase(clientLaunch.getBrowserType())) {
            if (clientLaunch.isHeadless()) {
                browser = Browser.FIREFOX_HEADLESS;
            } else {
                browser = Browser.FIREFOX;
            }
        } else if ("Chrome".equalsIgnoreCase(clientLaunch.getBrowserType())) {
            if (clientLaunch.isHeadless()) {
                browser = Browser.CHROME_HEADLESS;
            } else {
                browser = Browser.CHROME;
            }
        } else {
            LOGGER.debug(
                    "Browser not launched by ZAP, passing to Zest {}",
                    clientLaunch.getBrowserType());
            return null;
        }

        LOGGER.debug("Browser being launched by ZAP {}", clientLaunch.getBrowserType());
        String url = clientLaunch.getUrl();
        if (StringUtils.isNotEmpty(url)) {
            url = replaceVariablesInString(url, true);
        }
        WebDriver wd;
        String proxy = this.getProxy();
        if (StringUtils.isNotEmpty(proxy) && proxy.indexOf(':') > 0) {
            // Proxy should be host:port
            String[] proxyArray = proxy.split(":");
            wd =
                    extSel.getWebDriver(
                            HttpSender.PROXY_INITIATOR,
                            browser.getId(),
                            proxyArray[0],
                            Integer.parseInt(proxyArray[1]));
            if (wd != null && StringUtils.isNotEmpty(url)) {
                wd.get(url);
            }
        } else {
            wd = extSel.getProxiedBrowser(browser.getId(), url);
        }

        if (wd != null) {
            this.addWebDriver(clientLaunch.getWindowHandle(), wd);
            LOGGER.debug(
                    "Browser launched by ZAP {} {}",
                    clientLaunch.getBrowserType(),
                    clientLaunch.getWindowHandle());
            return clientLaunch.getWindowHandle();
        }
        return null;
    }

    private ScanPolicy getDefaultScanPolicy() {
        // Dont cache as the user can change the default
        ExtensionActiveScan extAscan =
                (ExtensionActiveScan)
                        Control.getSingleton()
                                .getExtensionLoader()
                                .getExtension(ExtensionActiveScan.NAME);
        if (extAscan != null) {
            return extAscan.getPolicyManager().getDefaultScanPolicy();
        }
        return null;
    }

    private void invokeScan(ZestScript script, ZestActionScan scan) throws ZestActionFailException {
        LOGGER.debug("invokeScan {}", scan.getElementType());
        this.alerts = new ArrayList<>();

        ScannerParam scannerParam = new ScannerParam();
        RuleConfigParam ruleConfigParam = null;
        ExtensionRuleConfig extRC =
                Control.getSingleton().getExtensionLoader().getExtension(ExtensionRuleConfig.class);
        if (extRC != null) {
            ruleConfigParam = extRC.getRuleConfigParam();
        }
        Scanner scanner = new Scanner(scannerParam, getDefaultScanPolicy(), ruleConfigParam);
        scanner.setScanChildren(false);
        scanner.addScannerListener(this);

        if (this.lastResult != null) {
            SiteNode fakeRoot = new SiteNode(null, ZEST_HISTORY_REFERENCE_TYPE, "");
            SiteNode sn = new SiteNode(null, ZEST_HISTORY_REFERENCE_TYPE, "");
            sn.setHistoryReference(this.lastResult);
            fakeRoot.add(sn);
            scanning = true;
            scanner.setStartNode(sn);
            scanner.start(sn);

            while (scanning) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        }
        if (alerts.size() > 0) {
            // Add all to alerts tab, flags in Script results..
            this.lastResult.setPassed(false);
            this.lastResult.setMessage(alerts.get(0).getName());
            extension.notifyChanged(this.lastResult);
        }
    }

    public boolean isStop() {
        return isStop;
    }

    public void pause() {
        this.pause = true;
    }

    public void resume() {
        this.pause = false;
    }

    public boolean isPaused() {
        return pause;
    }

    @Override
    public void scannerComplete(int id) {
        this.scanning = false;
    }

    @Override
    public void hostNewScan(int id, String hostAndPort, HostProcess hostThread) {}

    @Override
    public void hostProgress(int id, String hostAndPort, String msg, int percentage) {}

    @Override
    public void hostComplete(int id, String hostAndPort) {}

    @Override
    public void alertFound(Alert alert) {
        this.alerts.add(alert);

        ExtensionAlert extAlert =
                (ExtensionAlert)
                        Control.getSingleton()
                                .getExtensionLoader()
                                .getExtension(ExtensionAlert.NAME);
        if (extAlert != null) {
            extAlert.alertFound(alert, alert.getHistoryRef());
        }

        extension.notifyAlert(alert);
    }

    public HttpMessage getTarget() {
        return target;
    }

    public void setTarget(HttpMessage target) {
        this.target = target;
    }

    @Override
    public void notifyNewMessage(HttpMessage msg) {
        try {
            ZestResultWrapper zrw =
                    new ZestResultWrapper(
                            Model.getSingleton().getSession(),
                            ZEST_HISTORY_REFERENCE_TYPE,
                            msg,
                            -1);
            zrw.setType(ZestResultWrapper.Type.scanAction);

            this.notifyResponse(zrw);

        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public void setWrapper(ZestScriptWrapper wrapper) {
        this.wrapper = wrapper;
    }

    @Override
    public String getVariable(String name) {
        if (LOGGER.isDebugEnabled()) {
            String value = super.getVariable(name);
            String val = value;
            if (value != null) {
                val = value.replace("\n", " ");
                if (val.length() > 100) {
                    val = val.substring(0, 100) + "...";
                }
            }
            LOGGER.debug("getVariable {} : {}", name, val);

            return value;
        } else {
            return super.getVariable(name);
        }
    }

    @Override
    public void setVariable(String name, String value) {
        if (LOGGER.isDebugEnabled()) {
            String val = value;
            if (value != null) {
                val = value.replace("\n", " ");
                if (val.length() > 100) {
                    val = val.substring(0, 100) + "...";
                }
            }
            LOGGER.debug("setVariable {} = {}", name, val);
            super.setVariable(name, value);
        } else {
            super.setVariable(name, value);
        }
    }

    @Override
    public String getGlobalVariable(String name) {
        return ScriptVars.getGlobalVar(name);
    }

    @Override
    public void setGlobalVariable(String name, String value) {
        ScriptVars.setGlobalVar(name, value);
    }

    @Override
    public ZestResponse send(ZestRequest request) throws IOException {
        if (request.getUrl() == null) {
            throw new IOException("Request does not contain a request-uri.");
        }
        return super.send(request);
    }
}
