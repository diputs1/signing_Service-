package com.lifetex.sign.desktop.service;

import com.lifetex.sign.desktop.model.TokenProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class TokenConfigService {

    private final ObjectMapper mapper = new ObjectMapper();
    private File configFile;
    private List<TokenProfile> profiles = new ArrayList<>();

    public TokenConfigService() {
        initConfigFile();
        loadConfig();
        if (profiles.isEmpty()) {
            // Add default example
            profiles.add(new TokenProfile(UUID.randomUUID().toString(), "Default Token",
                    "C:\\Windows\\System32\\hiloca_csp11_v1.dll", 0));
            saveConfig();
        }
    }

    private void initConfigFile() {
        try {
            // Change path to User Home Directory to avoid permission issues in Program
            // Files
            String userHome = System.getProperty("user.home");
            File configDir = new File(userHome, ".lifetex-signing");
            if (!configDir.exists()) {
                boolean created = configDir.mkdirs();
                if (created) {
                    log.info("Created config directory: {}", configDir.getAbsolutePath());
                }
            }
            this.configFile = new File(configDir, "token-config.json");
            log.info("Using token config file at: {}", this.configFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to initialize config file path", e);
            // Fallback to current dir if something goes wrong
            this.configFile = new File("token-config.json");
        }
    }

    public List<TokenProfile> getAllProfiles() {
        return new ArrayList<>(profiles);
    }

    public void saveProfile(TokenProfile profile) {
        if (profile.getId() == null || profile.getId().isEmpty()) {
            profile.setId(UUID.randomUUID().toString());
            profiles.add(profile);
        } else {
            // Update
            for (int i = 0; i < profiles.size(); i++) {
                if (profiles.get(i).getId().equals(profile.getId())) {
                    profiles.set(i, profile);
                    break;
                }
            }
        }
        saveConfig();
    }

    public void deleteProfile(String id) {
        profiles.removeIf(p -> p.getId().equals(id));
        saveConfig();
    }

    public Optional<TokenProfile> getProfile(String id) {
        return profiles.stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    private void loadConfig() {
        if (configFile.exists()) {
            try {
                profiles = mapper.readValue(configFile, new TypeReference<List<TokenProfile>>() {
                });
                log.info("Loaded {} token profiles", profiles.size());
            } catch (IOException e) {
                log.error("Failed to load token config", e);
                profiles = new ArrayList<>();
            }
        } else {
            log.info("No token config found, starting fresh.");
        }
    }

    private void saveConfig() {
        try {
            mapper.writeValue(configFile, profiles);
        } catch (IOException e) {
            log.error("Failed to save token config to " + configFile.getAbsolutePath(), e);
        }
    }
}
