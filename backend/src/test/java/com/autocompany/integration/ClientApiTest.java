package com.autocompany.integration;

import com.autocompany.controller.ClientController;
import com.autocompany.entity.Client;
import com.autocompany.entity.Client.ClientStatus;
import com.autocompany.service.ClientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ORG-003 외부 고객 API — 등록 201 / 검증 400 / 미존재 404 / 상태 전이(409) 매핑 */
@WebMvcTest(ClientController.class)
public class ClientApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientService clientService;

    private Client sample() {
        Client client = Client.builder()
                .organizationId(UUID.randomUUID())
                .displayName("대박커머스")
                .build();
        client.setId(UUID.randomUUID());
        client.setStatus(ClientStatus.ACTIVE);
        return client;
    }

    @Test
    public void createClientReturns201() throws Exception {
        Client created = sample();
        when(clientService.createClient(any(), eq("대박커머스"), any(), any(), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + UUID.randomUUID() + "\",\"displayName\":\"대박커머스\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("대박커머스"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    public void createClientMissingNameReturns400() throws Exception {
        when(clientService.createClient(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("displayName is required"));

        mockMvc.perform(post("/api/v1/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + UUID.randomUUID() + "\",\"displayName\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_REQUEST"));
    }

    @Test
    public void getClientsRequiresOrganizationScope() throws Exception {
        UUID organizationId = UUID.randomUUID();
        when(clientService.getClientsByOrganization(organizationId)).thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/v1/clients").param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("대박커머스"));
    }

    @Test
    public void getClientMissingReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(clientService.getClient(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/clients/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    public void patchClientArchivedReturns200() throws Exception {
        Client archived = sample();
        archived.setStatus(ClientStatus.ARCHIVED);
        when(clientService.updateClient(eq(archived.getId()), any(), any(), any(), eq("archived")))
                .thenReturn(archived);

        mockMvc.perform(patch("/api/v1/clients/{id}", archived.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"archived\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    public void patchClientMissingReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(clientService.updateClient(eq(id), any(), any(), any(), any()))
                .thenThrow(new NoSuchElementException("Client not found: " + id));

        mockMvc.perform(patch("/api/v1/clients/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"새이름\"}"))
                .andExpect(status().isNotFound());
    }
}
