package com.lifetex.sign.model.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RectPosition {
    private int page;
    private float x;
    private float y;
    private float width;
    private float height;

    public RectPosition(float xDirAdj, float yDirAdj, float width, float height, int page) {
        this.x = xDirAdj;
        this.y = yDirAdj;
        this.width = width;
        this.height = height;
        this.page = page;
    }
}
