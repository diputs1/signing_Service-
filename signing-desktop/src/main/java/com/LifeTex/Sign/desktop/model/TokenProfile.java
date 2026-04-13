package com.lifetex.sign.desktop.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenProfile {
    private String id;
    private String name; // e.g. "Token Viettel"
    private String libraryPath; // e.g. "C:\Windows\System32\viettel-ca.dll"
    private int slotIndex;

    // Optional: Last used, serial, etc.
}
