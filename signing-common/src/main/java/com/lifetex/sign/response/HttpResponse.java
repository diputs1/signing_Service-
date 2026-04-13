package com.lifetex.sign.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class HttpResponse<T> {

    private Integer errorCode = 0;

    @Builder.Default
    private boolean success = false;

    @Builder.Default
    private String message = "";

    @Builder.Default
    private T data = null;
}
