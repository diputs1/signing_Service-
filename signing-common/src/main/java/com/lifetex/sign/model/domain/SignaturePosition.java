package com.lifetex.sign.model.domain;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignaturePosition {
    private int page;
    private float x;
    private float y;
    private float width;
    private float height;
}
