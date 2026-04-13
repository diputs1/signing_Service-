package com.lifetex.sign.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@AllArgsConstructor
public class GetFileResponse {
    MultipartFile file;
}
