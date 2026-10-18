package com.autocompany.controller;

import com.autocompany.entity.Client;
import com.autocompany.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * 외부 고객(중계) API (§5.26, ORG-003) — MVP 로그인 없음, 운영자가 등록·중계.
 * 중계 세션 기록은 source=client_relayed + 원문 보존 + 감사(§5.25).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @PostMapping("/clients")
    public ResponseEntity<Client> createClient(@RequestBody ClientRequest request) {
        Client client = clientService.createClient(request.organizationId, request.displayName,
                request.contact, request.notes, request.createdByUserId);
        return ResponseEntity.status(201).body(client);
    }

    @GetMapping("/clients")
    public ResponseEntity<List<Client>> getClients(@RequestParam UUID organizationId) {
        return ResponseEntity.ok(clientService.getClientsByOrganization(organizationId));
    }

    @GetMapping("/clients/{id}")
    public ResponseEntity<Client> getClient(@PathVariable UUID id) {
        return clientService.getClient(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NoSuchElementException("Client not found: " + id));
    }

    @PatchMapping("/clients/{id}")
    public ResponseEntity<Client> updateClient(@PathVariable UUID id, @RequestBody ClientPatchRequest request) {
        return ResponseEntity.ok(clientService.updateClient(id, request.displayName,
                request.contact, request.notes, request.status));
    }

    // ==================== 예외 매핑 ====================

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(Map.of("error_code", "CLIENT_STATE_CONFLICT", "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(400).body(Map.of("error_code", "INVALID_REQUEST", "message", e.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(404).body(Map.of("error_code", "NOT_FOUND", "message", e.getMessage()));
    }

    // ==================== DTO ====================

    public record ClientRequest(UUID organizationId, String displayName, String contact,
                                String notes, UUID createdByUserId) {
    }

    public record ClientPatchRequest(String displayName, String contact, String notes, String status) {
    }
}
