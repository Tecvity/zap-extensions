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
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.core.scanner.Plugin.AlertThreshold;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.network.HttpRequestHeader;
import org.zaproxy.addon.commonlib.CommonAlertTag;
import org.zaproxy.addon.commonlib.PolicyTag;

class ViewStateScanRuleUnitTest extends PassiveScannerTest<ViewstateScanRule> {

    private HttpMessage msg;
    private HttpRequestHeader header;

    @BeforeEach
    void before() throws URIException {
        msg = new HttpMessage();
        header = new HttpRequestHeader();

        msg.setRequestHeader(header);
        header.setURI(new URI("http://example.com", true));
    }

    @Override
    protected ViewstateScanRule createScanner() {
        return new ViewstateScanRule();
    }

    @Test
    void shouldReturnExpectedMappings() {
        // Given / When
        Map<String, String> tags = rule.getAlertTags();
        // Then
        assertThat(tags.size(), is(equalTo(3)));
        assertThat(
                tags.containsKey(CommonAlertTag.OWASP_2021_A04_INSECURE_DESIGN.getTag()),
                is(equalTo(true)));
        assertThat(
                tags.containsKey(CommonAlertTag.OWASP_2017_A06_SEC_MISCONFIG.getTag()),
                is(equalTo(true)));
        assertThat(tags.containsKey(PolicyTag.PENTEST.getTag()), is(equalTo(true)));
        assertThat(
                tags.get(CommonAlertTag.OWASP_2021_A04_INSECURE_DESIGN.getTag()),
                is(equalTo(CommonAlertTag.OWASP_2021_A04_INSECURE_DESIGN.getValue())));
        assertThat(
                tags.get(CommonAlertTag.OWASP_2017_A06_SEC_MISCONFIG.getTag()),
                is(equalTo(CommonAlertTag.OWASP_2017_A06_SEC_MISCONFIG.getValue())));
    }

    @Test
    void shouldReturnExpectedExampleAlerts() {
        // Given / When
        List<Alert> alerts = rule.getExampleAlerts();
        // Then
        assertThat(alerts.size(), is(equalTo(6)));
        Alert alert = alerts.get(0);
        assertThat(alert.getName(), is(equalTo("Potential IP Addresses Found in the Viewstate")));
        assertThat(
                alert.getDescription(),
                is(
                        equalTo(
                                "Potential IP addresses were found being serialized in the viewstate field.")));
        assertThat(alert.getOtherInfo(), is(equalTo("[192.168.1.1]")));
        assertThat(
                alert.getSolution(),
                is(equalTo("Verify the provided information isn't confidential.")));
        assertThat(alert.getAlertRef(), is(equalTo("10032-1")));
        assertThat(alert.getRisk(), is(equalTo(Alert.RISK_MEDIUM)));
        assertThat(alert.getConfidence(), is(equalTo(Alert.CONFIDENCE_MEDIUM)));
        alert = alerts.get(1);
        assertThat(alert.getName(), is(equalTo("Emails Found in the Viewstate")));
        assertThat(
                alert.getDescription(),
                is(equalTo("Email addresses were found being serialized in the viewstate field.")));
        assertThat(alert.getOtherInfo(), is(equalTo("[test@example.com]")));
        assertThat(
                alert.getSolution(),
                is(equalTo("Verify the provided information isn't confidential.")));
        assertThat(alert.getAlertRef(), is(equalTo("10032-2")));
        assertThat(alert.getRisk(), is(equalTo(Alert.RISK_MEDIUM)));
        assertThat(alert.getConfidence(), is(equalTo(Alert.CONFIDENCE_MEDIUM)));
        alert = alerts.get(2);
        assertThat(alert.getName(), is(equalTo("Old Asp.Net Version in Use")));
        assertThat(
                alert.getDescription(),
                is(equalTo("This website uses ASP.NET version 1.0 or 1.1.")));
        assertThat(
                alert.getSolution(),
                is(equalTo("Ensure the engaged framework is still supported by Microsoft.")));
        assertThat(alert.getAlertRef(), is(equalTo("10032-3")));
        assertThat(alert.getRisk(), is(equalTo(Alert.RISK_LOW)));
        assertThat(alert.getConfidence(), is(equalTo(Alert.CONFIDENCE_MEDIUM)));
        alert = alerts.get(3);
        assertThat(alert.getName(), is(equalTo("Viewstate without MAC Signature (Unsure)")));
        assertThat(
                alert.getDescription(),
                is(equalTo("This website uses ASP.NET's Viewstate but maybe without any MAC.")));
        assertThat(
                alert.getSolution(),
                is(equalTo("Ensure the MAC is set for all pages on this website.")));
        assertThat(alert.getAlertRef(), is(equalTo("10032-4")));
        assertThat(alert.getRisk(), is(equalTo(Alert.RISK_HIGH)));
        assertThat(alert.getConfidence(), is(equalTo(Alert.CONFIDENCE_LOW)));
        alert = alerts.get(4);
        assertThat(alert.getName(), is(equalTo("Viewstate without MAC Signature (Sure)")));
        assertThat(
                alert.getDescription(),
                is(equalTo("This website uses ASP.NET's Viewstate but without any MAC.")));
        assertThat(
                alert.getSolution(),
                is(equalTo("Ensure the MAC is set for all pages on this website.")));
        assertThat(alert.getAlertRef(), is(equalTo("10032-5")));
        assertThat(alert.getRisk(), is(equalTo(Alert.RISK_HIGH)));
        assertThat(alert.getConfidence(), is(equalTo(Alert.CONFIDENCE_MEDIUM)));
        alert = alerts.get(5);
        assertThat(alert.getName(), is(equalTo("Split Viewstate in Use")));
        assertThat(
                alert.getDescription(),
                is(
                        equalTo(
                                "This website uses ASP.NET's Viewstate and its value is split into several chunks.")));
        assertThat(
                alert.getSolution(),
                is(
                        equalTo(
                                "None - this may be a deliberate tuning of the configuration as this isn't the default setting.")));
        assertThat(alert.getAlertRef(), is(equalTo("10032-6")));
        assertThat(alert.getRisk(), is(equalTo(Alert.RISK_INFO)));
        assertThat(alert.getConfidence(), is(equalTo(Alert.CONFIDENCE_LOW)));
    }

    @Test
    @Override
    public void shouldHaveValidReferences() {
        super.shouldHaveValidReferences();
    }

    void shouldNotRaiseAlertAsThereIsNoContent() {
        scanHttpResponseReceive(msg);

        assertThat(alertsRaised.size(), equalTo(0));
    }

    @Test
    void shouldNotRaiseAlertAsThereIsNoValidViewState() {
        msg.setResponseBody("<input name=\"__specialstate\">");

        scanHttpResponseReceive(msg);

        assertThat(alertsRaised.size(), equalTo(0));
    }

    @Test
    void shouldNotRaiseAlertAsThereIsAnUnknownVersionOfASP() {
        msg.setResponseBody("<input name=\"__specialstate\" value=\"bm90dmFsaWQ=\">");

        scanHttpResponseReceive(msg);

        assertThat(alertsRaised.size(), equalTo(0));
    }

    @Test
    void shouldRaiseAlertAsThereIsNoValidMACUnsureLowThreshold() {
        // Given
        msg.setResponseBody(
                "<input name=\"__VIEWSTATE\" value=\"/wEPDWUKMTkwNjc4NTIwMWRkaKrolbpTKYmPUNsab597kh8iOBU=\">");
        rule.setAlertThreshold(AlertThreshold.LOW);
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), equalTo(1));
        assertThat(alertsRaised.get(0).getRisk(), equalTo(Alert.RISK_HIGH));
        assertThat(
                alertsRaised.get(0).getName(), equalTo("Viewstate without MAC Signature (Unsure)"));
        assertThat(alertsRaised.get(0).getWascId(), equalTo(14));
        assertThat(alertsRaised.get(0).getCweId(), equalTo(642));
        assertThat(alertsRaised.get(0).getAlertRef(), equalTo("10032-4"));
        assertSame(msg, alertsRaised.get(0).getMessage());
    }

    @ParameterizedTest
    @EnumSource(
            value = AlertThreshold.class,
            names = {"MEDIUM", "HIGH"})
    void shouldNotRaiseAlertAsThereIsNoValidMACUnsureMediumOrHighThreshold(
            AlertThreshold threshold) {
        // Given
        msg.setResponseBody(
                "<input name=\"__VIEWSTATE\" value=\"/wEPDWUKMTkwNjc4NTIwMWRkaKrolbpTKYmPUNsab597kh8iOBU=\">");
        rule.setAlertThreshold(threshold);
        // When
        scanHttpResponseReceive(msg);
        // Then
        assertThat(alertsRaised.size(), equalTo(0));
    }

    @Test
    void shouldRaiseAlertAsThereIsNoValidMACSure() {
        msg.setResponseBody(
                "<input name=\"__VIEWSTATE\" value=\"/wEPDWUKMTkwNjc4NTIwwEPDWUKMTkwNjc4NTIwMWRkaMWRka\">");

        scanHttpResponseReceive(msg);

        assertThat(alertsRaised.size(), equalTo(1));
        assertThat(alertsRaised.get(0).getRisk(), equalTo(Alert.RISK_HIGH));
        assertThat(
                alertsRaised.get(0).getName(), equalTo("Viewstate without MAC Signature (Sure)"));
        assertThat(alertsRaised.get(0).getWascId(), equalTo(14));
        assertThat(alertsRaised.get(0).getCweId(), equalTo(642));
        assertThat(alertsRaised.get(0).getAlertRef(), equalTo("10032-5"));
        assertSame(msg, alertsRaised.get(0).getMessage());
    }

    @Test
    void shouldRaiseAlertForOldASPVersion() {
        msg.setResponseBody(
                "<input name=\"__VIEWSTATE\" value=\"dDPDWUKMTkwNjc4NTIwMWRkaKrolbpTKYmPUNsab597kh8iOBU=\">");

        scanHttpResponseReceive(msg);

        assertThat("There should be one alert raised.", alertsRaised.size(), equalTo(1));
        assertThat(alertsRaised.get(0).getRisk(), equalTo(Alert.RISK_LOW));
        assertThat(alertsRaised.get(0).getName(), equalTo("Old Asp.Net Version in Use"));
        assertThat(alertsRaised.get(0).getWascId(), equalTo(14));
        assertThat(alertsRaised.get(0).getCweId(), equalTo(642));
        assertThat(alertsRaised.get(0).getConfidence(), equalTo(Alert.CONFIDENCE_MEDIUM));
        assertThat(alertsRaised.get(0).getAlertRef(), equalTo("10032-3"));
        assertSame(msg, alertsRaised.get(0).getMessage());
    }

    @Test
    void shouldNotRaiseAlertAsTheParametersDoNotHaveEmailsOrIps() {
        msg.setResponseBody(
                "<input name=\"__VIEWSTATE\" value=\"/wEPDwUJODczNjQ5OTk0D2QWAgIDD2QWAgIFDw8WAh4EVGV4dAUWSSBMb3ZlIERvdG5ldEN1cnJ5LmNvbWRkZMHbBY9JqBTvB5/6kXnY15AUSAwa\">");

        scanHttpResponseReceive(msg);

        assertThat(alertsRaised.size(), equalTo(0));
    }

    @Test
    void shouldRaiseAlertBecauseTheParametersDoesHaveEmail() {
        String encodedViewstate = getViewstateWithText("test@test.com");
        msg.setResponseBody("<input name=\"__VIEWSTATE\" value=\"" + encodedViewstate + "\">");

        scanHttpResponseReceive(msg);

        assertThat(alertsRaised.size(), equalTo(1));
        assertThat(alertsRaised.get(0).getRisk(), equalTo(Alert.RISK_MEDIUM));
        assertThat(alertsRaised.get(0).getConfidence(), equalTo(Alert.CONFIDENCE_MEDIUM));
        assertThat(alertsRaised.get(0).getName(), equalTo("Emails Found in the Viewstate"));
        assertThat(
                alertsRaised.get(0).getDescription(),
                equalTo("Email addresses were found being serialized in the viewstate field."));
        assertThat(alertsRaised.get(0).getOtherInfo(), equalTo("[Itest@test.com]"));
        assertThat(alertsRaised.get(0).getWascId(), equalTo(14));
        assertThat(alertsRaised.get(0).getCweId(), equalTo(642));
        assertThat(alertsRaised.get(0).getAlertRef(), equalTo("10032-2"));
        assertSame(msg, alertsRaised.get(0).getMessage());
    }

    @Test
    void shouldRaiseAlertBecauseTheParametersDoesHaveIP() {
        String encodedViewstate = getViewstateWithText("127.0.0.1");
        msg.setResponseBody("<input name=\"__VIEWSTATE\" value=\"" + encodedViewstate + "\">");

        scanHttpResponseReceive(msg);

        assertThat(alertsRaised.size(), equalTo(1));
        assertThat(alertsRaised.get(0).getRisk(), equalTo(Alert.RISK_MEDIUM));
        assertThat(alertsRaised.get(0).getConfidence(), equalTo(Alert.CONFIDENCE_MEDIUM));
        assertThat(
                alertsRaised.get(0).getName(),
                equalTo("Potential IP Addresses Found in the Viewstate"));
        assertThat(
                alertsRaised.get(0).getDescription(),
                equalTo(
                        "Potential IP addresses were found being serialized in the viewstate field."));
        assertThat(alertsRaised.get(0).getOtherInfo(), equalTo("[127.0.0.1]"));
        assertThat(alertsRaised.get(0).getWascId(), equalTo(14));
        assertThat(alertsRaised.get(0).getCweId(), equalTo(642));
        assertThat(alertsRaised.get(0).getAlertRef(), equalTo("10032-1"));
        assertSame(msg, alertsRaised.get(0).getMessage());
    }

    @Test
    void shouldRaiseAlertAsViewstateIsSplit() {
        msg.setResponseBody(
                "<input type=\"hidden\" name=\"__VIEWSTATEFIELDCOUNT\" id=\"__VIEWSTATEFIELDCOUNT\" value=\"3\" />"
                        + "<input type=\"hidden\" name=\"__VIEWSTATE\" id=\"__VIEWSTATE\" value=\"/wEPDwUKLTk2Njk3OTQxNg9kFgICAw9kFgICCQ88\" />"
                        + "<input type=\"hidden\" name=\"__VIEWSTATE1\" id=\"__VIEWSTATE1\" value=\"KwANAGQYAQUJR3JpZFZpZXcxD2dk4sjERFfnDXV/\" />"
                        + "<input type=\"hidden\" name=\"__VIEWSTATE2\" id=\"__VIEWSTATE2\" value=\"hMFGAL10HQUnZbk=\" />");

        scanHttpResponseReceive(msg);

        assertThat(alertsRaised.size(), equalTo(1));
        assertThat(alertsRaised.get(0).getRisk(), equalTo(Alert.RISK_INFO));
        assertThat(alertsRaised.get(0).getName(), equalTo("Split Viewstate in Use"));
        assertThat(alertsRaised.get(0).getWascId(), equalTo(14));
        assertThat(alertsRaised.get(0).getCweId(), equalTo(642));
        assertThat(alertsRaised.get(0).getAlertRef(), equalTo("10032-6"));
        assertSame(msg, alertsRaised.get(0).getMessage());
    }

    /**
     * Helper function for injecting a piece of text into a valid base64 encoded ASP Viewstate.
     *
     * @param inject the string to inject
     * @return a base64 encoded string with the inject value injected at byte 40.
     */
    private static String getViewstateWithText(String inject) {
        String base =
                "/wEPDwUJODczNjQ5OTk0D2QWAgIDD2QWAgIFDw8WAh4EVGV4dAUWSSBMb3ZlIERvdG5ldEN1cnJ5LmNvbWRkZMHbBY9JqBTvB5/6kXnY15AUSAwa";
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        }

        byte[] part1 = Arrays.copyOf(decoded, 40);
        byte[] part2 = Arrays.copyOfRange(decoded, 40, decoded.length);
        byte[] injectBytes = inject.getBytes();

        byte[] result = new byte[part1.length + injectBytes.length + part2.length];

        System.arraycopy(part1, 0, result, 0, part1.length);
        System.arraycopy(injectBytes, 0, result, part1.length, injectBytes.length);
        System.arraycopy(part2, 0, result, part1.length + injectBytes.length, part2.length);

        return Base64.getEncoder().encodeToString(result);
    }
}
