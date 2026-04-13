package com.lifetex.sign.service;

import com.lifetex.sign.exception.EJBCAException;
import com.lifetex.sign.model.dto.EnrollKeystoreRequestDTO;
import com.lifetex.sign.model.dto.EnrollKeystoreResponseDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
public class EJBCAService {
    private final String baseUrl;
    private final String caName;
    private final String certificateProfile;
    private final String endEntityProfile;
    private final RestTemplate restTemplate;

    public EJBCAService(
            @Qualifier("ejbcaRestTemplate") RestTemplate restTemplate,
            @Value("${ejbca.base-url}") String baseUrl,
            @Value("${ejbca.ca-name}") String caName,
            @Value("${ejbca.certificate-profile}") String certificateProfile,
            @Value("${ejbca.end-entity-profile}") String endEntityProfile) {

        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.caName = caName;
        this.certificateProfile = certificateProfile;
        this.endEntityProfile = endEntityProfile;

        // log.info("=== EJBCA Service Initialized ===");
        // log.info("RestTemplate class: {}", restTemplate.getClass().getName());
        // log.info("Base URL: {}", baseUrl);
        // log.info("CA Name: {}", caName);
        // log.info("Certificate Profile: {}", certificateProfile);
        // log.info("End Entity Profile: {}", endEntityProfile);

        // Validate config
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalStateException("ejbca.base-url is not configured!");
        }
    }

    /**
     * Lấy certificate mới trong EJBCA
     */

    public EnrollKeystoreResponseDTO enrollKeystore(String username, String password) {
        String url = baseUrl + "/certificate/enrollkeystore";

        EnrollKeystoreRequestDTO request = EnrollKeystoreRequestDTO.builder()
                .username(username)
                .password(password)
                .keyAlg("RSA")
                .keySpec("2048")
                .build();

        try {
            log.info("Dang ky kho khoa cho nguoi dung: {}", username);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<EnrollKeystoreRequestDTO> httpEntity = new HttpEntity<>(request, headers);

            ResponseEntity<EnrollKeystoreResponseDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    httpEntity,
                    EnrollKeystoreResponseDTO.class);

            log.info("Dang ky kho khoa cho user: {} thanh cong", username);
            return response.getBody();

        } catch (HttpClientErrorException e) {
            String errorBody = e.getResponseBodyAsString();
            log.error("EJBCA error: {}", errorBody);

            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode json = mapper.readTree(errorBody);

                String errorMessage = json.path("error_message").asText("");

                if (errorMessage.contains("does not exist")) {
                    throw new EJBCAException(404,
                            "User '" + username + "' không tồn tại. "
                                    + "Vui lòng admin tạo End Entity trong EJBCA Admin Web.");
                }

                if (errorMessage.contains("already exists") || errorMessage.contains("GENERATED")) {
                    throw new EJBCAException(403,
                            "User '" + username + "' đã được cấp certificate. "
                                    + "Vui lòng admin reset status trong EJBCA Admin Web về NEW.");
                }

                if (errorMessage.contains("invalid password")) {
                    throw new EJBCAException(403,
                            "Mật khẩu không đúng cho user: " + username);
                }

            } catch (Exception ignore) {
                // Ignore JSON parsing errors
            }

            throw new EJBCAException(
                    e.getStatusCode().value(),
                    "Lỗi enroll keystore: " + errorBody);
        }
    }

    /**
     * Kiểm tra người dùng tồn tại trong EJBCA
     */

    public boolean endEntityExists(String username) {
        String url = baseUrl + "/endentity/" + username;

        try {
            log.info("Kiểm tra tồn tại End Entity: {}", username);

            HttpEntity<EnrollKeystoreRequestDTO> httpEntity = new HttpEntity<>(null);
            ResponseEntity<Map> response = restTemplate.exchange(url,
                    HttpMethod.GET,
                    httpEntity,
                    Map.class);
            log.info("End Entity tồn tại: {}", username);
            if (response.getStatusCode() == HttpStatus.OK) {
                return true;
            } else {
                log.error("Lỗi không xác định khi kiểm tra End Entity: {}", username);
                return false;
            }

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("End Entity không tồn tại: {}", username);
                return false;
            } else {
                log.error("Lỗi khi kiểm tra End Entity: {}", e.getResponseBodyAsString());
                throw new EJBCAException(
                        e.getStatusCode().value(),
                        "Lỗi khi kiểm tra End Entity",
                        e);
            }
        }
    }

    public void resetEndEntityStatus(String username, String password) {
        String url = baseUrl + "/endentity/" + username + "/setstatus";

        Map<String, Object> request = Map.of(
                "password", password,
                "token", "P12",
                "status", "NEW");

        try {
            log.info("Resetting End Entity status to NEW for user: {}", username);
            restTemplate.postForObject(url, request, Void.class);
            log.info("Successfully reset status for user: {}", username);

        } catch (HttpClientErrorException e) {
            log.error("Failed to reset End Entity status: {}", e.getResponseBodyAsString());
            throw new EJBCAException(
                    e.getStatusCode().value(),
                    "Failed to reset End Entity status",
                    e);
        }
    }

    /**
     * Tạo End Entity mới trong EJBCA
     */
    public EnrollKeystoreResponseDTO createEndEntity(String username, String password, String email) {
        String url = baseUrl + "/endentity";

        Map<String, Object> request = Map.of(
                "username", username,
                "password", password,
                "subject_dn", "CN=" + username,
                "ca_name", caName,
                "certificate_profile_name", certificateProfile,
                "end_entity_profile_name", endEntityProfile,
                "token", "P12",
                "email", email != null ? email : username + "@example.com");

        try {
            log.info("Creating End Entity: {}", username);
            EnrollKeystoreResponseDTO endEntity = restTemplate.postForObject(url, request,
                    EnrollKeystoreResponseDTO.class);
            log.info("Successfully created End Entity: {}", username);
            return endEntity;

        } catch (HttpClientErrorException e) {
            log.error("Loi khi tao end entity: {}", e.getResponseBodyAsString());
            throw new EJBCAException(
                    e.getStatusCode().value(),
                    "Loi khi tao end entity",
                    e);
        }
    }

    /**
     * Kiểm tra trạng thái hoạt động của EJBCA
     */
    public boolean isHealthy() {
        String url = baseUrl + "/certificate/status";

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.error("EJBCA health check failed", e);
            return false;
        }
    }

}
