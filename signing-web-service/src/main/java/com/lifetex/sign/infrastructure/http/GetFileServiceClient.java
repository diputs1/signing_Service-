package com.lifetex.sign.infrastructure.http;

import com.lifetex.sign.model.dto.GetFileRequest;
import com.lifetex.sign.model.dto.GetFileResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class GetFileServiceClient extends BaseWebClient {
    @Value("${get-file.service.base-url}")
    private String baseUrl;

    public GetFileServiceClient(WebClient webClient) {
        super(webClient);
    }

    public Mono<GetFileResponse> getFile(GetFileRequest request) {
        return handleResponse(
                webClient.get()
                        .uri(request.getFullUrl())
                        .retrieve(),
                GetFileResponse.class);
    }

    public ResponseEntity<byte[]> downloadFile(String url) {
        return webClient.get()
                .uri(url)
                .retrieve()
                .toEntity(byte[].class)
                .block();
    }
}
