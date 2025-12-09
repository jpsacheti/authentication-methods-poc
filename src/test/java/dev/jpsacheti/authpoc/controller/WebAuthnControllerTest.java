package dev.jpsacheti.authpoc.controller;

import dev.jpsacheti.authpoc.dto.AuthDtos;
import dev.jpsacheti.authpoc.service.WebAuthnService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WebAuthnControllerTest {

    private MockMvc mockMvc;
    private WebAuthnService webAuthnService;

    @BeforeEach
    void setup() {
        webAuthnService = Mockito.mock(WebAuthnService.class);
        WebAuthnController controller = new WebAuthnController(webAuthnService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(
                        new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter()
                )
                .build();
    }

    @Test
    void startRegistration_ShouldReturnOk() throws Exception {
        when(webAuthnService.startRegistration(anyString(), anyString())).thenReturn("{}");

        mockMvc.perform(post("/webauthn/register/start")
                        .param("username", "user")
                        .param("attachment", "platform"))
                .andExpect(status().isOk());
    }

    @Test
    void startRegistration_WithNullAttachment_ShouldReturnOk() throws Exception {
        when(webAuthnService.startRegistration(anyString(), any())).thenReturn("{}");

        mockMvc.perform(post("/webauthn/register/start")
                        .param("username", "testuser"))
                .andExpect(status().isOk());
    }

    @Test
    void finishRegistration_ShouldReturnOk() throws Exception {
        String credentialJson = "{\"id\":\"test\",\"type\":\"public-key\"}";

        mockMvc.perform(post("/webauthn/register/finish")
                        .param("username", "testuser")
                        .content(credentialJson)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(webAuthnService).finishRegistration("testuser", credentialJson);
    }

    @Test
    void startLogin_ShouldReturnOk() throws Exception {
        String challenge = "{\"challenge\":\"abc123\"}";
        when(webAuthnService.startLogin("testuser")).thenReturn(challenge);

        mockMvc.perform(post("/webauthn/login/start")
                        .param("username", "testuser"))
                .andExpect(status().isOk())
                .andExpect(content().string(challenge));

        verify(webAuthnService).startLogin("testuser");
    }

    @Test
    void finishLogin_ShouldReturnAuthResponse() throws Exception {
        String credentialJson = "{\"id\":\"test\",\"type\":\"public-key\"}";
        AuthDtos.AuthResponse authResponse = new AuthDtos.AuthResponse("test-jwt-token");
        when(webAuthnService.finishLogin("testuser", credentialJson)).thenReturn(authResponse);

        mockMvc.perform(post("/webauthn/login/finish")
                        .param("username", "testuser")
                        .content(credentialJson)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-jwt-token"));

        verify(webAuthnService).finishLogin("testuser", credentialJson);
    }
}
