/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2014 The ZAP Development Team
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
package org.zaproxy.zap.extension.pscanrules;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.htmlparser.jericho.Source;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.addon.commonlib.CommonAlertTag;
import org.zaproxy.addon.commonlib.PolicyTag;
import org.zaproxy.zap.extension.pscan.PluginPassiveScanner;

/**
 * A class to passively scan response for signatures that are indicative that Directory Browsing /
 * Listing is enabled
 *
 * @author 70pointer@gmail.com
 */
public class DirectoryBrowsingScanRule extends PluginPassiveScanner
        implements CommonPassiveScanRuleInfo {

    /**
     * a consistently ordered map of: a regular expression pattern, mapping to the Web server to
     * which the pattern most likely corresponds
     */
    static Map<Pattern, String> serverPatterns = new LinkedHashMap<>();

    static {
        // Apache 2
        serverPatterns.put(
                Pattern.compile(
                        "<title>Index of /[^<]+?</title>", Pattern.MULTILINE | Pattern.DOTALL),
                "Apache 2");
        // TODO: add patterns here for other web servers, once these are available
        // IIS 7.5 follows this pattern. What about other versions??
        serverPatterns.put(
                Pattern.compile(
                        "<pre><A\\s+HREF\\s*=\\s*\"/[^>]*\">\\[To Parent Directory\\]</A><br><br>",
                        Pattern.MULTILINE | Pattern.DOTALL),
                "Microsoft IIS");
    }

    /** Prefix for internationalized messages used by this rule */
    private static final String MESSAGE_PREFIX = "pscanrules.directorybrowsing.";

    private static final Map<String, String> ALERT_TAGS;

    static {
        Map<String, String> alertTags =
                new HashMap<>(
                        CommonAlertTag.toMap(
                                CommonAlertTag.OWASP_2021_A05_SEC_MISCONFIG,
                                CommonAlertTag.OWASP_2017_A06_SEC_MISCONFIG));
        alertTags.put(PolicyTag.PENTEST.getTag(), "");
        alertTags.put(PolicyTag.QA_STD.getTag(), "");
        ALERT_TAGS = Collections.unmodifiableMap(alertTags);
    }

    /**
     * gets the name of the scanner
     *
     * @return
     */
    @Override
    public String getName() {
        return Constant.messages.getString(MESSAGE_PREFIX + "name");
    }

    /**
     * scans the HTTP response for signatures that might indicate Directory Browsing
     *
     * @param msg
     * @param id
     * @param source unused
     */
    @Override
    public void scanHttpResponseReceive(HttpMessage msg, int id, Source source) {
        // get the body contents as a String, so we can match against it
        String responsebody = msg.getResponseBody().toString();

        // try each of the patterns in turn against the response.
        String evidence = null;
        String server = null;
        Iterator<Pattern> patternIterator = serverPatterns.keySet().iterator();
        while (patternIterator.hasNext()) {
            Pattern serverPattern = patternIterator.next();
            server = serverPatterns.get(serverPattern);
            Matcher matcher = serverPattern.matcher(responsebody);
            if (matcher.find()) {
                evidence = matcher.group();
                break; // use the first match
            }
        }
        if (evidence != null && evidence.length() > 0) {
            // we found something
            buildAlert(server, evidence).raise();
        }
    }

    private AlertBuilder buildAlert(String server, String evidence) {
        return newAlert()
                .setName(getName())
                .setRisk(Alert.RISK_MEDIUM)
                .setConfidence(Alert.CONFIDENCE_MEDIUM)
                .setDescription(Constant.messages.getString(MESSAGE_PREFIX + "desc"))
                .setOtherInfo(Constant.messages.getString(MESSAGE_PREFIX + "extrainfo", server))
                .setSolution(Constant.messages.getString(MESSAGE_PREFIX + "soln"))
                .setReference(Constant.messages.getString(MESSAGE_PREFIX + "refs"))
                .setEvidence(evidence)
                .setCweId(548) // Information Exposure Through Directory Listing
                .setWascId(16); // Directory Indexing
    }

    @Override
    public List<Alert> getExampleAlerts() {
        return List.of(buildAlert("Apache 2", "<title>Index of /htdocs</title>").build());
    }

    /**
     * get the id of the scanner
     *
     * @return
     */
    @Override
    public int getPluginId() {
        return 10033;
    }

    @Override
    public Map<String, String> getAlertTags() {
        return ALERT_TAGS;
    }
}
