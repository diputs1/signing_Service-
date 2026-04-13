package com.lifetex.sign.desktop.controller;

import com.lifetex.sign.model.dto.SignRequestDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.web.multipart.MultipartFile;

@Data
@EqualsAndHashCode(callSuper = true)
public class LocalSignRequestDTO extends SignRequestDTO {
    private MultipartFile file;
    private String pkcs11LibraryPath;
    private String password;
    private Integer slotIndex = 0;
}
