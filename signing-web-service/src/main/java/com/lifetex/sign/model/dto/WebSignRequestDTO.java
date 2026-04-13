package com.lifetex.sign.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.web.multipart.MultipartFile;

@Data
@EqualsAndHashCode(callSuper = true)
public class WebSignRequestDTO extends SignRequestDTO {
    @NotNull(message = "File không được để trống")
    private MultipartFile file;
}
