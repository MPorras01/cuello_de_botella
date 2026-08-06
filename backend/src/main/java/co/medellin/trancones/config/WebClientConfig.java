package co.medellin.trancones.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean("googleWebClient")
    public WebClient googleWebClient(TrafficProperties props) {
        WebClient.Builder builder = WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024));
        if (hasBase(props.getGoogle().getBaseUrl())) {
            builder.baseUrl(props.getGoogle().getBaseUrl());
        }
        if (props.getGoogle().getApiKey() != null && !props.getGoogle().getApiKey().isBlank()) {
            builder.defaultHeader("X-Goog-Api-Key", props.getGoogle().getApiKey());
        }
        return builder.build();
    }

    @Bean("wazeWebClient")
    public WebClient wazeWebClient(TrafficProperties props) {
        WebClient.Builder builder = WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024));
        if (hasBase(props.getWaze().getBaseUrl())) {
            builder.baseUrl(props.getWaze().getBaseUrl());
        }
        return builder.build();
    }

    @Bean("simmWebClient")
    public WebClient simmWebClient(TrafficProperties props) {
        return WebClient.builder()
                .baseUrl(props.getSimm().getBaseUrl())
                .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                .build();
    }

    @Bean("tomtomWebClient")
    public WebClient tomtomWebClient(TrafficProperties props) {
        return WebClient.builder()
                .baseUrl(props.getTomtom().getBaseUrl())
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    private boolean hasBase(String baseUrl) {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
