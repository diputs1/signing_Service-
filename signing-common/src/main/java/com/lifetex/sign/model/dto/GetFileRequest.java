package com.lifetex.sign.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class GetFileRequest {
    String fullUrl;
}
