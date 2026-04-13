package com.lifetex.sign.desktop.service;

import eu.europa.esig.dss.token.Pkcs11SignatureToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.KeyStore.PasswordProtection;

@Slf4j
@Service
public class Pkcs11Service {

    public Pkcs11SignatureToken getToken(String libraryPath, String pin) {
        log.info("Loading PKCS#11 library from: {}", libraryPath);
        return new Pkcs11SignatureToken(libraryPath, new PasswordProtection(pin.toCharArray()));
    }

    public Pkcs11SignatureToken getToken(String libraryPath, String pin, int slotIndex) {
        log.info("Loading PKCS#11 library from: {} at slot {}", libraryPath, slotIndex);
        return new Pkcs11SignatureToken(libraryPath, new PasswordProtection(pin.toCharArray()), slotIndex);
    }
}
