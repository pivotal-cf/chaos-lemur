/*
 * Copyright 2014-2016 the original author or authors.
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

package io.pivotal.strepsirrhini.chaoslemur.infrastructure;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
final class StandardDirectorUtils implements DirectorUtils {

    @Autowired
    InfrastructureConfiguration config;

    @Autowired
    Set<ClientHttpRequestInterceptor> interceptors;

    private RestTemplate restTemplate() throws GeneralSecurityException {
        return createRestTemplate(interceptors);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<String> getDeployments() {
        URI deploymentsUri = UriComponentsBuilder.fromUri(config.getBoshUri())
            .path("deployments")
            .build().toUri();

        List<Map<String, String>> deployments = null;
        try {
            RestTemplate r = restTemplate();
            deployments = r.getForObject(deploymentsUri, List.class);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }

        return deployments.stream()
            .map(deployment -> deployment.get("name"))
            .collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<Map<String, String>> getVirtualMachines(String deployment) {
        URI vmsUri = UriComponentsBuilder.fromUri(config.getBoshUri())
            .pathSegment("deployments", deployment, "vms")
            .build().toUri();

        Set<Map<String, String>> result = null;
        try {
            result = restTemplate().getForObject(vmsUri, Set.class);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }

        return result;
    }

    private RestTemplate createRestTemplate(Set<ClientHttpRequestInterceptor> interceptors) throws GeneralSecurityException {

        SSLContext sslContext = SSLContexts.custom()
            .loadTrustMaterial(null, new TrustSelfSignedStrategy())
            .useTLS()
            .build();

        SSLConnectionSocketFactory connectionFactory = new SSLConnectionSocketFactory(sslContext, new AllowAllHostnameVerifier());

        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create()
            .disableRedirectHandling()
            .setSSLSocketFactory(connectionFactory);


        if (config.boshAuthType.equals(BoshAuthType.BASIC_AUTH)) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(new AuthScope(config.boshHost, 25555),
                new UsernamePasswordCredentials(config.boshUser, config.boshPassword));
            httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
        }

        HttpClient httpClient = httpClientBuilder.build();

        RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
        restTemplate.getInterceptors().addAll(interceptors);

        return restTemplate;
    }

}
