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
package org.zaproxy.zap.extension.ascanrules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.HTMLElementName;
import net.htmlparser.jericho.Source;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.core.scanner.AbstractAppParamPlugin;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.core.scanner.Category;
import org.parosproxy.paros.network.HttpHeader;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.addon.commonlib.CommonAlertTag;
import org.zaproxy.addon.commonlib.PolicyTag;
import org.zaproxy.addon.commonlib.http.HttpFieldsNames;
import org.zaproxy.addon.commonlib.vulnerabilities.Vulnerabilities;
import org.zaproxy.addon.commonlib.vulnerabilities.Vulnerability;

/**
 * Reviewed scan rule for External Redirect
 *
 * @author yhawke (2014)
 */
public class ExternalRedirectScanRule extends AbstractAppParamPlugin
        implements CommonActiveScanRuleInfo {

    /** Prefix for internationalised messages used by this rule */
    private static final String MESSAGE_PREFIX = "ascanrules.externalredirect.";

    private static final Map<String, String> ALERT_TAGS;

    static {
        Map<String, String> alertTags =
                new HashMap<>(
                        CommonAlertTag.toMap(
                                CommonAlertTag.OWASP_2021_A03_INJECTION,
                                CommonAlertTag.OWASP_2017_A01_INJECTION,
                                CommonAlertTag.WSTG_V42_CLNT_04_OPEN_REDIR,
                                CommonAlertTag.HIPAA));
        alertTags.put(PolicyTag.API.getTag(), "");
        alertTags.put(PolicyTag.DEV_CICD.getTag(), "");
        alertTags.put(PolicyTag.DEV_STD.getTag(), "");
        alertTags.put(PolicyTag.DEV_FULL.getTag(), "");
        alertTags.put(PolicyTag.QA_STD.getTag(), "");
        alertTags.put(PolicyTag.QA_FULL.getTag(), "");
        alertTags.put(PolicyTag.SEQUENCE.getTag(), "");
        alertTags.put(PolicyTag.PENTEST.getTag(), "");
        ALERT_TAGS = Collections.unmodifiableMap(alertTags);
    }

    private static final int PLUGIN_ID = 20019;
    private static final String ORIGINAL_VALUE_PLACEHOLDER = "@@@original@@@";

    // ZAP: Added multiple redirection types
    public static final int NO_REDIRECT = 0x00;
    public static final int REDIRECT_LOCATION_HEADER = 0x01;
    public static final int REDIRECT_REFRESH_HEADER = 0x02;
    public static final int REDIRECT_LOCATION_META = 0x03;
    public static final int REDIRECT_REFRESH_META = 0x04;
    public static final int REDIRECT_HREF_BASE = 0x05;
    public static final int REDIRECT_JAVASCRIPT = 0x06;

    private static final String OWASP_SUFFIX = ".owasp.org";
    // Use a random 'host' to prevent false positives/collisions
    // Something like: 8519918658030487947.owasp.org
    // Only need part of the UUID and abs so that we don't get negatives
    private static final String SITE_HOST =
            Long.toString(Math.abs(UUID.randomUUID().getMostSignificantBits()));
    private static final String REDIRECT_SITE = SITE_HOST + OWASP_SUFFIX;

    /** The various (prioritized) payload to try */
    private static final String[] REDIRECT_TARGETS = {
        REDIRECT_SITE,
        HttpHeader.SCHEME_HTTPS + REDIRECT_SITE,
        HttpHeader.SCHEME_HTTPS + REDIRECT_SITE.replace(".", "%2e"), // Double encode the dots
        "5;URL='https://" + REDIRECT_SITE + "'",
        "URL='http://" + REDIRECT_SITE + "'",
        // Simple allow list bypass, ex: https://evil.com?<original_value>
        // Where "original_value" is whatever the parameter value initially was, ex:
        // https://good.expected.com
        HttpHeader.SCHEME_HTTPS + REDIRECT_SITE + "/?" + ORIGINAL_VALUE_PLACEHOLDER,
        "5;URL='https://" + REDIRECT_SITE + "/?" + ORIGINAL_VALUE_PLACEHOLDER + "'",
        HttpHeader.SCHEME_HTTPS + "\\" + REDIRECT_SITE,
        HttpHeader.SCHEME_HTTP + "\\" + REDIRECT_SITE,
        HttpHeader.SCHEME_HTTP + REDIRECT_SITE,
        "//" + REDIRECT_SITE,
        "\\\\" + REDIRECT_SITE,
        "HtTp://" + REDIRECT_SITE,
        "HtTpS://" + REDIRECT_SITE,

        // http://kotowicz.net/absolute/
        // I never met real cases for these
        // to be evaluated in the future
        /*
        "/\\" + REDIRECT_SITE,
        "\\/" + REDIRECT_SITE,
        "\r \t//" + REDIRECT_SITE,
        "/ /" + REDIRECT_SITE,
        "http:" + REDIRECT_SITE, "https:" + REDIRECT_SITE,
        "http:/" + REDIRECT_SITE, "https:/" + REDIRECT_SITE,
        "http:////" + REDIRECT_SITE, "https:////" + REDIRECT_SITE,
        "://" + REDIRECT_SITE,
        ".:." + REDIRECT_SITE
        */
    };

    // Get WASC Vulnerability description
    private static final Vulnerability VULN = Vulnerabilities.getDefault().get("wasc_38");

    private static final Logger LOGGER = LogManager.getLogger(ExternalRedirectScanRule.class);

    @Override
    public int getId() {
        return PLUGIN_ID;
    }

    @Override
    public String getName() {
        return Constant.messages.getString(MESSAGE_PREFIX + "name");
    }

    @Override
    public String getDescription() {
        return VULN.getDescription();
    }

    @Override
    public int getCategory() {
        return Category.MISC;
    }

    @Override
    public String getSolution() {
        return VULN.getSolution();
    }

    @Override
    public String getReference() {
        return VULN.getReferencesAsString();
    }

    /**
     * Scan for External Redirect vulnerabilities
     *
     * @param msg a request only copy of the original message (the response isn't copied)
     * @param param the parameter name that need to be exploited
     * @param value the original parameter value
     */
    @Override
    public void scan(HttpMessage msg, String param, String value) {

        // Number of targets to try
        int targetCount = 0;

        // Debug only
        LOGGER.debug("Attacking at Attack Strength: {}", this.getAttackStrength());

        // Figure out how aggressively we should test
        switch (this.getAttackStrength()) {
            case LOW:
                // Check only for baseline targets (2 reqs / param)
                targetCount = 3;
                break;

            case MEDIUM:
                // This works out as a total of 9 reqs / param
                targetCount = 9;
                break;

            case HIGH:
                // This works out as a total of 15 reqs / param
                targetCount = REDIRECT_TARGETS.length;
                break;

            case INSANE:
                // This works out as a total of 15 reqs / param
                targetCount = REDIRECT_TARGETS.length;
                break;

            default:
                break;
        }

        LOGGER.debug(
                "Checking [{}][{}], parameter [{}] for Open Redirect Vulnerabilities",
                getBaseMsg().getRequestHeader().getMethod(),
                getBaseMsg().getRequestHeader().getURI(),
                param);

        // For each target in turn
        // note that depending on the AttackLevel,
        // the number of elements that we will try changes.
        String payload;
        String redirectUrl;

        for (int h = 0; h < targetCount; h++) {
            if (isStop()) {
                return;
            }

            payload =
                    REDIRECT_TARGETS[h].contains(ORIGINAL_VALUE_PLACEHOLDER)
                            ? REDIRECT_TARGETS[h].replace(ORIGINAL_VALUE_PLACEHOLDER, value)
                            : REDIRECT_TARGETS[h];

            // Get a new copy of the original message (request only) for each parameter value to try
            HttpMessage testMsg = getNewMsg();
            setParameter(testMsg, param, payload);

            LOGGER.debug("Testing [{}] = [{}]", param, payload);

            try {
                // Send the request and retrieve the response
                // Be careful: we haven't to follow redirect
                sendAndReceive(testMsg, false);

                String payloadScheme =
                        StringUtils.containsIgnoreCase(payload, HttpHeader.HTTPS)
                                ? HttpHeader.HTTPS
                                : HttpHeader.HTTP;
                // If it's a meta based injection the use the base url
                redirectUrl =
                        (payload.startsWith("5;") || payload.startsWith("URL="))
                                ? payloadScheme + "://" + REDIRECT_SITE
                                : payload;

                // Get back if a redirection occurs
                int redirectType = isRedirected(redirectUrl, testMsg);

                if (redirectType != NO_REDIRECT) {
                    // We Found IT!
                    // First do logging
                    LOGGER.debug(
                            "[External Redirection Found] on parameter [{}] with payload [{}]",
                            param,
                            payload);

                    buildAlert(param, payload, redirectType, redirectUrl, testMsg).raise();

                    // All done. No need to look for vulnerabilities on subsequent
                    // parameters on the same request (to reduce performance impact)
                    return;
                }
            } catch (IOException ex) {
                // Do not try to internationalize this.. we need an error message in any event..
                // if it's in English, it's still better than not having it at all.
                LOGGER.warn(
                        "External Redirect vulnerability check failed for parameter [{}] and payload [{}] due to an I/O error",
                        param,
                        payload,
                        ex);
            }
        }
    }

    private String getAlertReference(int redirectType) {
        switch (redirectType) {
            case REDIRECT_LOCATION_HEADER:
                return getId() + "-1";
            case REDIRECT_REFRESH_HEADER:
                return getId() + "-2";
            case REDIRECT_LOCATION_META:
            case REDIRECT_REFRESH_META:
                return getId() + "-3";
            case REDIRECT_JAVASCRIPT:
                return getId() + "-4";
            default:
                return "";
        }
    }

    private AlertBuilder buildAlert(
            String param, String payload, int redirectType, String evidence, HttpMessage testMsg) {
        String alertRef = getAlertReference(redirectType);

        return newAlert()
                .setConfidence(Alert.CONFIDENCE_MEDIUM)
                .setParam(param)
                .setAttack(payload)
                .setOtherInfo(getRedirectionReason(redirectType))
                .setEvidence(evidence)
                .setAlertRef(alertRef)
                .setMessage(testMsg);
    }

    private static final Pattern REFRESH_PATTERN =
            Pattern.compile("(?i)\\s*\\d+\\s*;\\s*url\\s*=\\s*[\"']?([^'\"]*)[\"']?");

    static String getRefreshUrl(String value) {
        Matcher matcher = REFRESH_PATTERN.matcher(value);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static final Pattern LOCATION_PATTERN =
            Pattern.compile("(?i)^\\s*url\\s*=\\s*[\"']?([^'\"]*)[\"']?");

    static String getLocationUrl(String value) {
        Matcher matcher = LOCATION_PATTERN.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Check if the payload is a redirect
     *
     * @param value the value retrieved
     * @return true if it's a valid open redirect
     */
    private static boolean checkPayload(String value) {
        if (value == null || !StringUtils.startsWithIgnoreCase(value, HttpHeader.HTTP)) {
            return false;
        }

        try {
            return isRedirectHost(value, true);
        } catch (URIException e) {
            LOGGER.debug(e.getMessage(), e);
            try {
                return isRedirectHost(value, false);
            } catch (URIException ex) {
                LOGGER.debug(ex.getMessage(), ex);
                return false;
            }
        }
    }

    private static boolean isRedirectHost(String value, boolean escaped) throws URIException {
        URI locUri = new URI(value, escaped);
        return REDIRECT_SITE.equalsIgnoreCase(locUri.getHost());
    }

    /**
     * Check if the evil payload has been reflected in the retrieved response inside one of the
     * possible redirection points. For a (quite) complete list of the possible redirection attacks
     * please refer to http://code.google.com/p/html5security/wiki/RedirectionMethods
     *
     * @param payload the payload that should be reflected inside a redirection point
     * @param msg the current message where reflected redirection should be check into
     * @return get back the redirection type if exists
     */
    private static int isRedirected(String payload, HttpMessage msg) {

        // (1) Check if redirection by "Location" header
        // http://en.wikipedia.org/wiki/HTTP_location
        // HTTP/1.1 302 Found
        // Location: http://www.example.org/index.php
        String value = msg.getResponseHeader().getHeader(HttpFieldsNames.LOCATION);
        if (checkPayload(value)) {
            return REDIRECT_LOCATION_HEADER;
        }

        // (2) Check if redirection by "Refresh" header
        // http://en.wikipedia.org/wiki/URL_redirection
        // HTTP/1.1 200 ok
        // Refresh: 0; url=http://www.example.com/
        value = msg.getResponseHeader().getHeader(HttpFieldsNames.REFRESH);
        if (value != null) {
            // Usually redirect content is configured with a delay
            // so extract the url component
            value = getRefreshUrl(value);

            if (checkPayload(value)) {
                return REDIRECT_REFRESH_HEADER;
            }
        }

        // (3) Check if redirection occurs by "Meta" content header
        // http://code.google.com/p/html5security/wiki/RedirectionMethods
        // <meta http-equiv="location" content="URL=http://evil.com" />
        // <meta http-equiv="refresh" content="0;url=http://evil.com/" />
        String content = msg.getResponseBody().toString();
        Source htmlSrc = new Source(content);
        List<Element> metaElements = htmlSrc.getAllElements(HTMLElementName.META);
        for (Element el : metaElements) {

            value = el.getAttributeValue("http-equiv");

            if (value != null) {
                if (value.equalsIgnoreCase(HttpFieldsNames.LOCATION)) {
                    // Get the content attribute value
                    value = getLocationUrl(el.getAttributeValue("content"));

                    // Check if the payload is inside the location attribute
                    if (checkPayload(value)) {
                        return REDIRECT_LOCATION_META;
                    }

                } else if (value.equalsIgnoreCase("refresh")) {
                    // Get the content attribute value
                    value = el.getAttributeValue("content");

                    // If the content attribute isn't set go away
                    if (value != null) {
                        // Usually redirect content is configured with a delay
                        // so extract the url component
                        value = getRefreshUrl(value);

                        // Check if the payload is inside the location attribute
                        if (checkPayload(value)) {
                            return REDIRECT_REFRESH_META;
                        }
                    }
                }
            }
        }

        // (4) Check if redirection occurs by Base Tag
        // http://code.google.com/p/html5security/wiki/RedirectionMethods
        // <base href="http://evil.com/" />

        // (5) Check if redirection occurs by Javascript
        // http://code.google.com/p/html5security/wiki/RedirectionMethods
        if (StringUtils.indexOfIgnoreCase(content, payload) != -1) {
            List<Element> jsElements = htmlSrc.getAllElements(HTMLElementName.SCRIPT);
            String matchingUrl = "https?://" + REDIRECT_SITE;
            Pattern pattern;

            for (Element el : jsElements) {
                value = el.getContent().toString();

                // location='http://evil.com/';
                // location.href='http://evil.com/';
                pattern =
                        Pattern.compile(
                                "(?i)location(?:\\.href)?\\s*=\\s*['\"](" + matchingUrl + ")['\"]");
                if (isRedirectPresent(pattern, value)) {
                    return REDIRECT_JAVASCRIPT;
                }

                // location.reload('http://evil.com/');
                // location.replace('http://evil.com/');
                // location.assign('http://evil.com/');
                pattern =
                        Pattern.compile(
                                "(?i)location\\.(?:replace|reload|assign)\\s*\\(\\s*['\"]("
                                        + matchingUrl
                                        + ")['\"]");
                if (isRedirectPresent(pattern, value)) {
                    return REDIRECT_JAVASCRIPT;
                }

                // window.open('http://evil.com/');
                // window.navigate('http://evil.com/');
                pattern =
                        Pattern.compile(
                                "(?i)window\\.(?:open|navigate)\\s*\\(\\s*['\"]("
                                        + matchingUrl
                                        + ")['\"]");
                if (isRedirectPresent(pattern, value)) {
                    return REDIRECT_JAVASCRIPT;
                }
            }
        }

        return NO_REDIRECT;
    }

    private static boolean isRedirectPresent(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find()
                && StringUtils.startsWithIgnoreCase(matcher.group(1), HttpHeader.HTTP);
    }

    /**
     * Get a readable reason for the found redirection
     *
     * @param type the redirection type
     * @return a string representing the reason of this redirection
     */
    private static String getRedirectionReason(int type) {
        switch (type) {
            case REDIRECT_LOCATION_HEADER:
                return Constant.messages.getString(MESSAGE_PREFIX + "reason.location.header");

            case REDIRECT_LOCATION_META:
                return Constant.messages.getString(MESSAGE_PREFIX + "reason.location.meta");

            case REDIRECT_REFRESH_HEADER:
                return Constant.messages.getString(MESSAGE_PREFIX + "reason.refresh.header");

            case REDIRECT_REFRESH_META:
                return Constant.messages.getString(MESSAGE_PREFIX + "reason.refresh.meta");

            case REDIRECT_JAVASCRIPT:
                return Constant.messages.getString(MESSAGE_PREFIX + "reason.javascript");

            default:
                return Constant.messages.getString(MESSAGE_PREFIX + "reason.notfound");
        }
    }

    /**
     * Give back the risk associated to this vulnerability (high)
     *
     * @return the risk according to the Alert enum
     */
    @Override
    public int getRisk() {
        return Alert.RISK_HIGH;
    }

    @Override
    public Map<String, String> getAlertTags() {
        return ALERT_TAGS;
    }

    /**
     * http://cwe.mitre.org/data/definitions/601.html
     *
     * @return the official CWE id
     */
    @Override
    public int getCweId() {
        return 601;
    }

    /**
     * http://projects.webappsec.org/w/page/13246981/URL%20Redirector%20Abuse
     *
     * @return the official WASC id
     */
    @Override
    public int getWascId() {
        return 38;
    }

    @Override
    public List<Alert> getExampleAlerts() {
        List<Alert> alerts = new ArrayList<>();
        String param = "destination";
        alerts.add(
                buildAlert(
                                param,
                                "http://3412390346190766618.owasp.org",
                                REDIRECT_LOCATION_HEADER,
                                getAlertReference(REDIRECT_LOCATION_HEADER),
                                null)
                        .build());
        alerts.add(
                buildAlert(
                                param,
                                "5;URL='https://3412390346190766618.owasp.org'",
                                REDIRECT_REFRESH_HEADER,
                                getAlertReference(REDIRECT_REFRESH_HEADER),
                                null)
                        .build());
        alerts.add(
                buildAlert(
                                param,
                                "5;URL='https://3412390346190766618.owasp.org'",
                                REDIRECT_REFRESH_META,
                                getAlertReference(REDIRECT_REFRESH_META),
                                null)
                        .build());
        alerts.add(
                buildAlert(
                                param,
                                "http://373082522675462941.owasp.org",
                                REDIRECT_JAVASCRIPT,
                                getAlertReference(REDIRECT_JAVASCRIPT),
                                null)
                        .build());
        return alerts;
    }
}
