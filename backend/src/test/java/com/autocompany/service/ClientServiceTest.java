package com.autocompany.service;

import com.autocompany.entity.Client;
import com.autocompany.entity.Client.ClientStatus;
import com.autocompany.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ORG-003 §5.26 Client 명부 서비스 — 등록 검증·부분 갱신·중계 가능성(active) 판정 */
public class ClientServiceTest {

    private ClientRepository clientRepository;
    private ClientService service;

    @BeforeEach
    void setUp() {
        clientRepository = mock(ClientRepository.class);
        service = new ClientService(clientRepository);
    }

    private Client client(UUID id, ClientStatus status) {
        Client client = Client.builder()
                .organizationId(UUID.randomUUID())
                .displayName("대박커머스")
                .build();
        client.setId(id);
        client.setStatus(status);
        return client;
    }

    @Test
    void createRequiresOrganizationAndDisplayName() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createClient(null, "대박커머스", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.createClient(UUID.randomUUID(), "   ", null, null, null));
    }

    @Test
    void createStartsActiveAndTrimsName() {
        UUID organizationId = UUID.randomUUID();
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        Client created = service.createClient(organizationId, "  대박커머스  ", "010-0000-0000", "메모", null);

        assertEquals("대박커머스", created.getDisplayName());
        assertEquals(organizationId, created.getOrganizationId());
        assertEquals(ClientStatus.ACTIVE, created.getStatus()); // §5.26 신규는 active 시작
    }

    @Test
    void listIsScopedByOrganization() {
        UUID organizationId = UUID.randomUUID();
        List<Client> rows = List.of(client(UUID.randomUUID(), ClientStatus.ACTIVE));
        when(clientRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)).thenReturn(rows);

        assertEquals(rows, service.getClientsByOrganization(organizationId));
    }

    @Test
    void updatePatchesOnlyProvidedFields() {
        UUID id = UUID.randomUUID();
        Client existing = client(id, ClientStatus.ACTIVE);
        existing.setContact("010-1111-1111");
        existing.setNotes("기존 메모");
        when(clientRepository.findById(id)).thenReturn(Optional.of(existing));
        when(clientRepository.save(existing)).thenReturn(existing);

        Client updated = service.updateClient(id, "  새이름 ", null, "", "archived");

        assertEquals("새이름", updated.getDisplayName()); // trim
        assertEquals("010-1111-1111", updated.getContact()); // null → 유지
        assertEquals("", updated.getNotes()); // 빈 문자열 → 지우기
        assertEquals(ClientStatus.ARCHIVED, updated.getStatus());
    }

    @Test
    void updateRejectsUnknownStatus() {
        UUID id = UUID.randomUUID();
        when(clientRepository.findById(id)).thenReturn(Optional.of(client(id, ClientStatus.ACTIVE)));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateClient(id, null, null, null, "deleted"));
        verify(clientRepository, never()).save(any());
    }

    @Test
    void updateMissingClientIsNotFound() {
        UUID id = UUID.randomUUID();
        when(clientRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> service.updateClient(id, "이름", null, null, null));
    }

    @Test
    void requireActiveForRelayReturnsActiveClient() {
        UUID id = UUID.randomUUID();
        Client active = client(id, ClientStatus.ACTIVE);
        when(clientRepository.findById(id)).thenReturn(Optional.of(active));

        assertEquals(active, service.requireActiveForRelay(id));
    }

    @Test
    void requireActiveForRelayRejectsArchived() {
        UUID id = UUID.randomUUID();
        when(clientRepository.findById(id)).thenReturn(Optional.of(client(id, ClientStatus.ARCHIVED)));

        // §5.26 — archived로의 신규 중계 연결은 409
        assertThrows(IllegalStateException.class, () -> service.requireActiveForRelay(id));
    }

    @Test
    void requireActiveForRelayMissingIsNotFound() {
        UUID id = UUID.randomUUID();
        when(clientRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.requireActiveForRelay(id));
    }
}
