/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2016 The ZAP Development Team
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.quality.Strictness;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.core.scanner.Plugin.AlertThreshold;
import org.parosproxy.paros.model.Model;
import org.parosproxy.paros.model.OptionsParam;
import org.parosproxy.paros.network.HttpMalformedHeaderException;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.network.HttpResponseHeader;
import org.zaproxy.addon.commonlib.CommonAlertTag;
import org.zaproxy.addon.commonlib.PolicyTag;
import org.zaproxy.addon.commonlib.http.HttpDateUtils;
import org.zaproxy.zap.extension.ruleconfig.RuleConfigParam;
import org.zaproxy.zap.utils.ZapXmlConfiguration;

class CookieSameSiteScanRuleUnitTest extends PassiveScannerTest<CookieSameSiteScanRule> {

    private Model model;

    @Override
    protected CookieSameSiteScanRule createScanner() {
        rule = new CookieSameSiteScanRule();
        // Mock the model and options
        model = mock(Model.class, withSettings().strictness(Strictness.LENIENT));
        OptionsParam options = new OptionsParam();
        ZapXmlConfiguration conf = new ZapXmlConfiguration();
        options.load(conf);
        when(model.getOptionsParam()).thenReturn(options);
        rule.setModel(model);
        return rule;
    }

    @Test
    void shouldReturnExpectedMappings() {
        // Given / When
        Map<String, String> tags = rule.getAlertTags();
        // Then
        assertThat(tags.size(), is(equalTo(6)));
        assertThat(
                tags.containsKey(CommonAlertTag.OWASP_2021_A01_BROKEN_AC.getTag()),
                is(equalTo(true)));
        assertThat(
                tags.containsKey(CommonAlertTag.OWASP_2017_A05_BROKEN_AC.getTag()),
                is(equalTo(true)));
        assertThat(
                tags.containsKey(CommonAlertTag.WSTG_V42_SESS_02_COOKIE_ATTRS.getTag()),
                is(equalTo(true)));
        assertThat(tags.containsKey(PolicyTag.PENTEST.getTag()), is(equalTo(true)));
        assertThat(tags.containsKey(PolicyTag.DEV_STD.getTag()), is(equalTo(true)));
        assertThat(tags.containsKey(PolicyTag.QA_STD.getTag()), is(equalTo(true)));
        assertThat(
                tags.get(CommonAlertTag.OWASP_2021_A01_BROKEN_AC.getTag()),
                is(equalTo(CommonAlertTag.OWASP_2021_A01_BROKEN_AC.getValue())));
        assertThat(
                tags.get(CommonAlertTag.OWASP_2017_A05_BROKEN_AC.getTag()),
                is(equalTo(CommonAlertTag.OWASP_2017_A05_BROKEN_AC.getValue())));
        assertThat(
                tags.get(CommonAlertTag.WSTG_V42_SESS_02_COOKIE_ATTRS.getTag()),
                is(equalTo(CommonAlertTag.WSTG_V42_SESS_02_COOKIE_ATTRS.getValue())));
    }

    @Test
    void shouldHaveExpectedExampleAlert() {
        // Given / When
        List<Alert> alerts = rule.getExampleAlerts();
        // THen
        assertThat(alerts.size(), is(equalTo(3)));
        Alert missingAlert = alerts.get(0);
        assertThat(missingAlert.getName(), is(equalTo("Cookie without SameSite Attribute")));
        assertThat(missingAlert.getAlertRef(), is(equalTo("10054-1")));
        Alert noneAlert = alerts.get(1);
        assertThat(noneAlert.getName(), is(equalTo("Cookie with SameSite Attribute None")));
        assertThat(noneAlert.getAlertRef(), is(equalTo("10054-2")));
        Alert badValueAlert = alerts.get(2);
        assertThat(badValueAlert.getName(), is(equalTo("Cookie with Invalid SameSite Attribute")));
        assertThat(badValueAlert.getAlertRef(), is(equalTo("10054-3")));
    }

    @Test
    @Override
    public void shouldHaveValidReferences() {
        super.shouldHaveValidReferences();
    }

    @Test
    void shouldAlertWhenNoSameSiteAttribute() throws HttpMalformedHeaderException {
        // Given
        HttpMessage msg = createMessage();
        msg.getResponseHeader().setHeader(HttpResponseHeader.SET_COOKIE, "test=123; Path=/");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), equalTo(1));
        assertThat("Cookie without SameSite Attribute", equalTo(alertsRaised.get(0).getName()));
        assertThat(alertsRaised.get(0).getParam(), equalTo("test"));
        assertThat(alertsRaised.get(0).getEvidence(), equalTo("set-cookie: test"));
    }

    @Test
    void shouldNotAlertWhenNoCookie() throws HttpMalformedHeaderException {
        // Given
        HttpMessage msg = createMessage();
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), equalTo(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "None", "nonE"})
    void shouldAlertGivenNoneSameSiteAttribute(String sameSite)
            throws HttpMalformedHeaderException {
        // Given
        HttpMessage msg = createMessage(sameSite);
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), equalTo(1));
        Alert alert = alertsRaised.get(0);
        assertEquals("Cookie with SameSite Attribute None", alert.getName());
        assertThat(alertsRaised.get(0).getParam(), equalTo("test"));
        assertThat(alertsRaised.get(0).getEvidence(), equalTo("set-cookie: test"));
    }

    @Test
    void shouldNotAlertOnNoneSameSiteAttributeHighThreshold() throws HttpMalformedHeaderException {
        // Given
        HttpMessage msg = createMessage("none");
        rule.setAlertThreshold(AlertThreshold.HIGH);
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), equalTo(0));
    }

    @Test
    void shouldNotAlertOnLaxSameSiteAttribute() throws HttpMalformedHeaderException {
        // Given
        HttpMessage msg = createMessage("Lax");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), equalTo(0));
    }

    @Test
    void shouldNotAlertOnStrictSameSiteAttribute() throws HttpMalformedHeaderException {
        // Given
        HttpMessage msg = createMessage("strICt");
        // WHen
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), equalTo(0));
    }

    @Test
    void shouldAlertOnceWhenSecondCookieNoSameSiteAttribute() throws HttpMalformedHeaderException {
        // Given
        HttpMessage msg = createMessage("lax");
        msg.getResponseHeader().addHeader(HttpResponseHeader.SET_COOKIE, "test=123; Path=/");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), equalTo(1));
        assertThat(alertsRaised.get(0).getParam(), equalTo("test"));
        assertThat(alertsRaised.get(0).getEvidence(), equalTo("set-cookie: test"));
    }

    @Test
    void shouldAlertWhenBadValSameSiteAttribute() throws HttpMalformedHeaderException {
        // Given
        HttpMessage msg = createMessage("badVal");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), equalTo(1));
        assertThat(alertsRaised.get(0).getParam(), equalTo("test"));
        assertThat(alertsRaised.get(0).getEvidence(), equalTo("set-cookie: test"));
    }

    @Test
    void shouldAlertWhenNoValSameSiteAttribute() throws HttpMalformedHeaderException {
        // Given
        HttpMessage msg = createMessage();
        msg.getResponseHeader()
                .setHeader(HttpResponseHeader.SET_COOKIE, "test=123; Path=/; SameSite");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), equalTo(1));
        assertThat(alertsRaised.get(0).getParam(), equalTo("test"));
        assertThat(alertsRaised.get(0).getEvidence(), equalTo("set-cookie: test"));
    }

    @Test
    void shouldAlertWhenEmptyValSameSiteAttribute() throws HttpMalformedHeaderException {
        // Given
        HttpMessage msg = createMessage();
        msg.getResponseHeader()
                .setHeader(HttpResponseHeader.SET_COOKIE, "test=123; Path=/; SameSite=");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), equalTo(1));
        assertThat(alertsRaised.get(0).getParam(), equalTo("test"));
        assertThat(alertsRaised.get(0).getEvidence(), equalTo("set-cookie: test"));
    }

    @Test
    void shouldNotAlertOnDelete() throws HttpMalformedHeaderException {
        // Given - value empty and epoch start date
        HttpMessage msg = createMessage();
        msg.getResponseHeader()
                .setHeader(
                        HttpResponseHeader.SET_COOKIE,
                        "test=\"\"; expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), equalTo(0));
    }

    @Test
    void shouldNotAlertOnDeleteHyphenatedDate() throws HttpMalformedHeaderException {
        // Given - value empty and epoch start date
        HttpMessage msg = createMessage();
        msg.getResponseHeader()
                .setHeader(
                        HttpResponseHeader.SET_COOKIE,
                        "test=\"\"; expires=Thu, 01-Jan-1970 00:00:00 GMT; Path=/");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), equalTo(0));
    }

    @Test
    void shouldAlertWhenFutureExpiry() throws HttpMalformedHeaderException {
        // Given - value empty, expiry in +1 year
        String expiry =
                HttpDateUtils.format(LocalDateTime.now().plusYears(1).toInstant(ZoneOffset.UTC));

        HttpMessage msg = createMessage();
        msg.getResponseHeader()
                .setHeader(
                        HttpResponseHeader.SET_COOKIE, "test=\"\"; expires=" + expiry + "; Path=/");
        // Then
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), equalTo(1));
        assertThat(alertsRaised.get(0).getParam(), equalTo("test"));
        assertThat(alertsRaised.get(0).getEvidence(), equalTo("set-cookie: test"));
    }

    @Test
    void shouldAlertOnceWhenSecondCookieNoSameSiteAttributeFirstExpired()
            throws HttpMalformedHeaderException {
        // Given
        HttpMessage msg = createMessage();
        msg.getResponseHeader()
                .setHeader(
                        HttpResponseHeader.SET_COOKIE,
                        "hasatt=test123; expires=Thu, 01-Jan-1970 00:00:00 GMT; Path=/");
        msg.getResponseHeader().addHeader(HttpResponseHeader.SET_COOKIE, "test=123; Path=/;");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), equalTo(1));
        assertThat(alertsRaised.get(0).getParam(), equalTo("test"));
        assertThat(alertsRaised.get(0).getEvidence(), equalTo("set-cookie: test"));
    }

    @Test
    void shouldNotAlertWhenCookieOnIgnoreList() throws HttpMalformedHeaderException {
        // Given
        model.getOptionsParam()
                .getConfig()
                .setProperty(RuleConfigParam.RULE_COOKIE_IGNORE_LIST, "aaaa,test,bbb");

        HttpMessage msg = createMessage();
        msg.getResponseHeader().setHeader(HttpResponseHeader.SET_COOKIE, "test=123; Path=/");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), equalTo(0));
    }

    @Test
    void shouldAlertWhenCookieNotOnIgnoreList() throws HttpMalformedHeaderException {
        // Given
        model.getOptionsParam()
                .getConfig()
                .setProperty(RuleConfigParam.RULE_COOKIE_IGNORE_LIST, "aaaa,bbb,ccc");

        HttpMessage msg = createMessage();
        msg.getResponseHeader().setHeader(HttpResponseHeader.SET_COOKIE, "test=123; Path=/");
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), equalTo(1));
        assertThat(alertsRaised.get(0).getParam(), equalTo("test"));
        assertThat(alertsRaised.get(0).getEvidence(), equalTo("set-cookie: test"));
    }

    private static HttpMessage createMessage() throws HttpMalformedHeaderException {
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader("GET https://www.example.com/test/ HTTP/1.1");
        msg.setResponseBody("<html></html>");
        msg.setResponseHeader(
                "HTTP/1.1 200 OK\r\n"
                        + "Server: Apache-Coyote/1.1\r\n"
                        + "Content-Type: text/html;charset=ISO-8859-1\r\n"
                        + "Content-Length: "
                        + msg.getResponseBody().length()
                        + "\r\n");
        return msg;
    }

    private static HttpMessage createMessage(String sameSite) throws HttpMalformedHeaderException {
        HttpMessage msg = createMessage();
        if (!sameSite.isEmpty()) {
            msg.getResponseHeader()
                    .setHeader(
                            HttpResponseHeader.SET_COOKIE,
                            "test=123; Path=/; SameSite=" + sameSite);
        }
        return msg;
    }
}
