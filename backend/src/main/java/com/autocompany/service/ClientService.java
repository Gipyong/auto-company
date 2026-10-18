package com.autocompany.service;

import com.autocompany.entity.Client;
import com.autocompany.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * §5.26 Client(외부 고객) 명부 — 운영자(사용자)가 등록·수정하고 중계한다.
 * MVP: 외부 고객 로그인 없음. archived 클라이언트는 신규 중계 연결 불가(409).
 */
@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;

    @Transactional
    public Client createClient(UUID organizationId, String displayName, String contact,
                               String notes, UUID createdByUserId) {
        if (organizationId == null) {
            throw new IllegalArgumentException("organizationId is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
        Client client = Client.builder()
                .organizationId(organizationId)
                .displayName(displayName.trim())
                .contact(contact)
                .notes(notes)
                .createdByUserId(createdByUserId)
                .build();
        return clientRepository.save(client);
    }

    @Transactional(readOnly = true)
    public List<Client> getClientsByOrganization(UUID organizationId) {
        return clientRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
    }

    @Transactional(readOnly = true)
    public Optional<Client> getClient(UUID id) {
        return clientRepository.findById(id);
    }

    /** 부분 갱신(PATCH 의미) — null 필드는 유지, 빈 문자열은 지우기로 반영한다 */
    @Transactional
    public Client updateClient(UUID id, String displayName, String contact, String notes, String status) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Client not found: " + id));
        if (displayName != null && !displayName.isBlank()) {
            client.setDisplayName(displayName.trim());
        }
        if (contact != null) {
            client.setContact(contact);
        }
        if (notes != null) {
            client.setNotes(notes);
        }
        if (status != null && !status.isBlank()) {
            try {
                client.setStatus(Client.ClientStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("status must be active|archived");
            }
        }
        return clientRepository.save(client);
    }

    /** 중계 연결용(프로젝트 생성·세션 개설) — active만 허용, archived는 409(§5.26) */
    @Transactional(readOnly = true)
    public Client requireActiveForRelay(UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new NoSuchElementException("Client not found: " + clientId));
        if (client.getStatus() != Client.ClientStatus.ACTIVE) {
            throw new IllegalStateException("Archived client cannot be used for new relays (§5.26: 409)");
        }
        return client;
    }
}
