package com.lifetex.sign.infrastructure.http;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public abstract class BaseWebClient {

    protected final WebClient webClient;

    protected BaseWebClient(WebClient webClient) {
        this.webClient = webClient;
    }

    protected <T> Mono<T> handleResponse(WebClient.ResponseSpec response, Class<T> clazz) {
        return response
                .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
                        .map(body -> new RuntimeException("Client error: " + body)))
                .onStatus(HttpStatusCode::is5xxServerError, r -> r.bodyToMono(String.class)
                        .map(body -> new RuntimeException("Server error: " + body)))
                .bodyToMono(clazz);
    }
}
