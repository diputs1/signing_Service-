package com.lifetex.sign.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageInsertRequest {
    private String keyWord;
    private String imagesBase;
    private float width;
    private float height;
}
