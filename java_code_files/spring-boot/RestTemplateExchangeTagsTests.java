/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.actuate.metrics.web.client;

import java.io.IOException;

import io.micrometer.core.instrument.Tag;
import org.junit.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link RestTemplateExchangeTags}.
 *
 * @author Nishant Raut
 * @author Brian Clozel
 * @author Brian Clozel
 */
public class RestTemplateExchangeTagsTests {

	@Test
	public void outcomeTagIsUnknownWhenResponseStatusIsNull() {
		Tag tag = RestTemplateExchangeTags.outcome(null);
		assertThat(tag.getValue()).isEqualTo("UNKNOWN");
	}

	@Test
	public void outcomeTagIsInformationalWhenResponseIs1xx() {
		ClientHttpResponse response = new MockClientHttpResponse("foo".getBytes(),
				HttpStatus.CONTINUE);
		Tag tag = RestTemplateExchangeTags.outcome(response);
		assertThat(tag.getValue()).isEqualTo("INFORMATIONAL");
	}

	@Test
	public void outcomeTagIsSuccessWhenResponseIs2xx() {
		ClientHttpResponse response = new MockClientHttpResponse("foo".getBytes(),
				HttpStatus.OK);
		Tag tag = RestTemplateExchangeTags.outcome(response);
		assertThat(tag.getValue()).isEqualTo("SUCCESS");
	}

	@Test
	public void outcomeTagIsRedirectionWhenResponseIs3xx() {
		ClientHttpResponse response = new MockClientHttpResponse("foo".getBytes(),
				HttpStatus.MOVED_PERMANENTLY);
		Tag tag = RestTemplateExchangeTags.outcome(response);
		assertThat(tag.getValue()).isEqualTo("REDIRECTION");
	}

	@Test
	public void outcomeTagIsClientErrorWhenResponseIs4xx() {
		ClientHttpResponse response = new MockClientHttpResponse("foo".getBytes(),
				HttpStatus.BAD_REQUEST);
		Tag tag = RestTemplateExchangeTags.outcome(response);
		assertThat(tag.getValue()).isEqualTo("CLIENT_ERROR");
	}

	@Test
	public void outcomeTagIsServerErrorWhenResponseIs5xx() {
		ClientHttpResponse response = new MockClientHttpResponse("foo".getBytes(),
				HttpStatus.BAD_GATEWAY);
		Tag tag = RestTemplateExchangeTags.outcome(response);
		assertThat(tag.getValue()).isEqualTo("SERVER_ERROR");
	}

	@Test
	public void outcomeTagIsUnknownWhenResponseThrowsIOException() throws Exception {
		ClientHttpResponse response = mock(ClientHttpResponse.class);
		given(response.getStatusCode()).willThrow(IOException.class);
		Tag tag = RestTemplateExchangeTags.outcome(response);
		assertThat(tag.getValue()).isEqualTo("UNKNOWN");
	}

	@Test
	public void outcomeTagIsUnknownForCustomResponseStatus() throws Exception {
		ClientHttpResponse response = mock(ClientHttpResponse.class);
		given(response.getStatusCode()).willThrow(IllegalArgumentException.class);
		Tag tag = RestTemplateExchangeTags.outcome(response);
		assertThat(tag.getValue()).isEqualTo("UNKNOWN");
	}

}
