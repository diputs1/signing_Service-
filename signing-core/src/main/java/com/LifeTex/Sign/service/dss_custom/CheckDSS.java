package com.lifetex.sign.service.dss_custom;

import eu.europa.esig.dss.pdf.pdfbox.visible.PdfBoxSignatureDrawerFactory;
import java.security.CodeSource;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CheckDSS {
    public static void main(String[] args) {
        System.out.println("=== DIAGNOSTIC START ===");
        try {
            CodeSource src = PdfBoxSignatureDrawerFactory.class.getProtectionDomain().getCodeSource();
            if (src != null) {
                System.out.println("Location: " + src.getLocation());
                if (src.getLocation().toString().endsWith(".jar")) {
                    System.out.println("Scanning JAR for classes...");
                    try (ZipInputStream zip = new ZipInputStream(src.getLocation().openStream())) {
                        ZipEntry entry;
                        while ((entry = zip.getNextEntry()) != null) {
                            String name = entry.getName();
                            if (name.endsWith(".class") && !name.contains("$")) {
                                // Filter readable names
                                if (name.contains("Factory") || name.contains("Drawer")) {
                                    System.out.println("Found: " + name.replace("/", ".").replace(".class", ""));
                                }
                            }
                        }
                    }
                } else {
                    System.out.println("Not a JAR file, mostly a directory. Check manually.");
                }
            } else {
                System.out.println("CodeSource is null.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("=== DIAGNOSTIC END ===");
    }
}
