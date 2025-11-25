package dev.jpsacheti.authpoc.controller;

import dev.jpsacheti.authpoc.dto.AuthDtos;
import dev.jpsacheti.authpoc.service.WebAuthnService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class WebAuthnControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebAuthnService webAuthnService;

    @Test
    void startRegistration_ShouldReturnOk() throws Exception {
        when(webAuthnService.startRegistration(anyString())).thenReturn("{}");

        mockMvc.perform(post("/webauthn/register/start")
                .param("username", "user"))
                .andExpect(status().isOk());
    }

    @Test
    void finishRegistration_ShouldReturnOk() throws Exception {
        mockMvc.perform(post("/webauthn/register/finish")
                .param("username", "user")
                .content("{}")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
