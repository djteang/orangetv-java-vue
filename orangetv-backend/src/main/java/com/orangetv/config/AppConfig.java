package com.orangetv.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

@Configuration
public class AppConfig {

    @Value("${LIVE_HTTP_PROXY:}")
    private String liveHttpProxy = "";

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder.json()
                .modules(new JavaTimeModule())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Bean
    @Primary
    public RestTemplate restTemplate() throws Exception {
        return createRestTemplate(false, false, "");
    }

    @Bean(name = "searchRestTemplate")
    public RestTemplate searchRestTemplate() throws Exception {
        return createRestTemplate(true, false, "");
    }

    @Bean(name = "liveRestTemplate")
    public RestTemplate liveRestTemplate() throws Exception {
        return createRestTemplate(false, true, liveHttpProxy);
    }

    @Bean(name = "liveMetadataRestTemplate")
    public RestTemplate liveMetadataRestTemplate() throws Exception {
        // 订阅、EPG 和 HEAD 检查需要普通响应处理，但与播放请求使用同一直播出口。
        return createRestTemplate(false, false, liveHttpProxy);
    }

    private RestTemplate createRestTemplate(boolean searchClient, boolean liveClient, String proxyUrl) throws Exception {
        var clientBuilder = HttpClients.custom();
        LiveHttpProxy.configure(clientBuilder, proxyUrl);

        // 构建信任所有证书的 SSL 上下文（CMS 视频源 API 常使用自签名/过期证书）
        SSLContext sslContext = SSLContextBuilder.create()
                .loadTrustMaterial(TrustAllStrategy.INSTANCE)
                .build();

        // 搜索和连续直播流各自限制连接与读取超时。
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(searchClient ? 3 : liveClient ? 5 : 30))
                .setSocketTimeout(Timeout.ofSeconds(searchClient ? 8 : liveClient ? 15 : 60))
                .build();

        // 搜索独立使用连接池，避免慢源影响直播和其他代理请求。
        HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                        .setSslContext(sslContext)
                        .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                        .build())
                .setDefaultConnectionConfig(connectionConfig)
                .setMaxConnTotal(searchClient ? 48 : 200)
                .setMaxConnPerRoute(searchClient ? 8 : 50)
                .build();

        // 搜索也限制等待连接的时间，防止连接池拥塞时长时间排队。
        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofSeconds(searchClient ? 8 : liveClient ? 15 : 60))
                .setConnectionRequestTimeout(Timeout.ofSeconds(searchClient ? 2 : liveClient ? 3 : 180))
                .setRedirectsEnabled(!liveClient)
                .build();

        clientBuilder.setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .disableCookieManagement();
        // 慢源只尝试一次，避免自动重试占用搜索连接池。
        if (searchClient || liveClient) clientBuilder.disableAutomaticRetries();
        CloseableHttpClient httpClient = clientBuilder.build();

        ClientHttpRequestFactory factory = liveClient ? new LiveHttpRequestFactory(httpClient)
                : new HttpComponentsClientHttpRequestFactory(httpClient);
        RestTemplate restTemplate = new RestTemplate(factory);

        // 添加支持所有媒体类型的 ByteArrayHttpMessageConverter
        ByteArrayHttpMessageConverter byteArrayConverter = new ByteArrayHttpMessageConverter();
        byteArrayConverter.setSupportedMediaTypes(Arrays.asList(
                MediaType.APPLICATION_OCTET_STREAM,
                MediaType.ALL,
                MediaType.parseMediaType("video/mp2t"),
                MediaType.parseMediaType("video/mp4"),
                MediaType.parseMediaType("application/vnd.apple.mpegurl"),
                MediaType.parseMediaType("audio/mpegurl"),
                MediaType.parseMediaType("application/x-mpegurl")
        ));
        restTemplate.getMessageConverters().add(0, byteArrayConverter);

        // 添加支持 m3u8 媒体类型的 StringHttpMessageConverter
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        stringConverter.setSupportedMediaTypes(Arrays.asList(
                MediaType.TEXT_PLAIN,
                MediaType.TEXT_HTML,
                MediaType.APPLICATION_JSON,
                MediaType.parseMediaType("application/vnd.apple.mpegurl"),
                MediaType.parseMediaType("audio/mpegurl"),
                MediaType.parseMediaType("application/x-mpegurl"),
                MediaType.parseMediaType("audio/x-mpegurl"),
                MediaType.ALL
        ));
        restTemplate.getMessageConverters().add(1, stringConverter);

        return restTemplate;
    }

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("OrangeTV-Async-");
        executor.initialize();
        return executor;
    }
}
