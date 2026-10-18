package com.autocompany.integration;

import com.autocompany.controller.TeamController;
import com.autocompany.entity.Team;
import com.autocompany.entity.Team.TeamStatus;
import com.autocompany.service.TeamService;
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

/** TEAM-001 팀(부서) API — 등록 201 / 검증 400 / 미존재 404 / 아카이브 가드(409) 매핑 (§5.3) */
@WebMvcTest(TeamController.class)
public class TeamApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeamService teamService;

    private Team sample() {
        Team team = Team.builder()
                .organizationId(UUID.randomUUID())
                .name("개발팀")
                .build();
        team.setId(UUID.randomUUID());
        team.setStatus(TeamStatus.ACTIVE);
        return team;
    }

    @Test
    public void createTeamReturns201() throws Exception {
        Team created = sample();
        when(teamService.createTeam(any(), eq("개발팀"), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + UUID.randomUUID() + "\",\"name\":\"개발팀\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("개발팀"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    public void createTeamBlankNameReturns400() throws Exception {
        when(teamService.createTeam(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("name is required"));

        mockMvc.perform(post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + UUID.randomUUID() + "\",\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("INVALID_REQUEST"));
    }

    @Test
    public void createTeamDuplicateNameReturns409() throws Exception {
        when(teamService.createTeam(any(), any(), any()))
                .thenThrow(new IllegalStateException("Team with this name already exists: 개발팀"));

        mockMvc.perform(post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + UUID.randomUUID() + "\",\"name\":\"개발팀\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("TEAM_STATE_CONFLICT"));
    }

    @Test
    public void getTeamsRequiresOrganizationScope() throws Exception {
        UUID organizationId = UUID.randomUUID();
        when(teamService.getTeamsByOrganization(organizationId)).thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/v1/teams").param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("개발팀"));
    }

    @Test
    public void getTeamMissingReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(teamService.getTeam(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/teams/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    public void patchTeamRenameReturns200() throws Exception {
        Team renamed = sample();
        renamed.setName("플랫폼팀");
        when(teamService.updateTeam(eq(renamed.getId()), eq("플랫폼팀"), any(), any())).thenReturn(renamed);

        mockMvc.perform(patch("/api/v1/teams/{id}", renamed.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"플랫폼팀\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("플랫폼팀"));
    }

    @Test
    public void patchTeamArchiveWithMembersReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(teamService.updateTeam(eq(id), any(), any(), eq("archived")))
                .thenThrow(new IllegalStateException("Cannot archive team with assigned employees. Reassign employees first."));

        mockMvc.perform(patch("/api/v1/teams/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"archived\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    public void patchTeamArchiveEmptyReturns200() throws Exception {
        Team archived = sample();
        archived.setStatus(TeamStatus.ARCHIVED);
        when(teamService.updateTeam(eq(archived.getId()), any(), any(), eq("archived"))).thenReturn(archived);

        mockMvc.perform(patch("/api/v1/teams/{id}", archived.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"archived\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    public void patchTeamMissingReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(teamService.updateTeam(eq(id), any(), any(), any()))
                .thenThrow(new NoSuchElementException("Team not found: " + id));

        mockMvc.perform(patch("/api/v1/teams/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새이름\"}"))
                .andExpect(status().isNotFound());
    }
}
