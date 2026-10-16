package com.autocompany.service;

import com.autocompany.entity.*;
import com.autocompany.entity.enums.*;
import com.autocompany.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class JobGradeServiceTest {

    @Autowired
    private JobGradeService jobGradeService;
    @Autowired
    private JobGradeRepository jobGradeRepository;
    @Autowired
    private GradeModelRepository gradeModelRepository;
    @Autowired
    private ModelProfileRepository modelProfileRepository;
    @Autowired
    private ModelProviderRepository modelProviderRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private UserRepository userRepository;

    private UUID organizationId;
    private UUID providerId;
    private UUID profile1Id;
    private UUID profile2Id;

    @BeforeEach
    public void setUp() {
        organizationId = UUID.randomUUID();

        ModelProvider provider = new ModelProvider();
        provider.setOrganizationId(organizationId);
        provider.setName("test-provider");
        provider.setProviderType(ModelProvider.ProviderType.OPENAI_COMPATIBLE);
        provider.setEnabled(true);
        provider.setHealthStatus(ModelProvider.HealthStatus.HEALTHY);
        provider = modelProviderRepository.save(provider);
        providerId = provider.getId();

        ModelProfile profile1 = new ModelProfile();
        profile1.setProvider(provider);
        profile1.setName("low-cost-model");
        profile1.setProviderModelId("gpt-4o-mini");
        profile1.setQualityTier(1);
        profile1.setEnabled(true);
        profile1 = modelProfileRepository.save(profile1);
        profile1Id = profile1.getId();

        ModelProfile profile2 = new ModelProfile();
        profile2.setProvider(provider);
        profile2.setName("high-quality-model");
        profile2.setProviderModelId("gpt-4o");
        profile2.setQualityTier(3);
        profile2.setEnabled(true);
        profile2 = modelProfileRepository.save(profile2);
        profile2Id = profile2.getId();
    }

    @Test
    public void testCreateGrade() {
        JobGrade grade = new JobGrade();
        grade.setOrganizationId(organizationId);
        grade.setCode("L1");
        grade.setName("Junior");
        grade.setOrdinal(1);
        grade.setModelSelectionMode(JobGrade.ModelSelectionMode.PRIORITY);
        grade.setStatus(JobGrade.Status.ACTIVE);

        JobGrade created = jobGradeService.createGrade(grade);
        assertNotNull(created.getId());
        assertEquals("L1", created.getCode());
        assertEquals("Junior", created.getName());
        assertEquals(1, created.getOrdinal());
    }

    @Test
    public void testCreateGradeDuplicateOrdinalFails() {
        JobGrade grade1 = new JobGrade();
        grade1.setOrganizationId(organizationId);
        grade1.setCode("L1");
        grade1.setName("Junior");
        grade1.setOrdinal(1);
        grade1.setModelSelectionMode(JobGrade.ModelSelectionMode.PRIORITY);
        grade1.setStatus(JobGrade.Status.ACTIVE);
        jobGradeService.createGrade(grade1);

        JobGrade grade2 = new JobGrade();
        grade2.setOrganizationId(organizationId);
        grade2.setCode("L1-DUP");
        grade2.setName("Junior Duplicate");
        grade2.setOrdinal(1); // Same ordinal
        grade2.setModelSelectionMode(JobGrade.ModelSelectionMode.PRIORITY);
        grade2.setStatus(JobGrade.Status.ACTIVE);

        assertThrows(IllegalArgumentException.class, () -> jobGradeService.createGrade(grade2));
    }

    @Test
    public void testGetGradesByOrganization() {
        for (int i = 1; i <= 3; i++) {
            JobGrade grade = new JobGrade();
            grade.setOrganizationId(organizationId);
            grade.setCode("L" + i);
            grade.setName("Level " + i);
            grade.setOrdinal(i);
            grade.setModelSelectionMode(JobGrade.ModelSelectionMode.PRIORITY);
            grade.setStatus(JobGrade.Status.ACTIVE);
            jobGradeService.createGrade(grade);
        }

        List<JobGrade> grades = jobGradeService.getGradesByOrganization(organizationId);
        assertEquals(3, grades.size());
        assertEquals(1, grades.get(0).getOrdinal());
        assertEquals(2, grades.get(1).getOrdinal());
        assertEquals(3, grades.get(2).getOrdinal());
    }

    @Test
    public void testSetAndGetGradeModels() {
        JobGrade grade = new JobGrade();
        grade.setOrganizationId(organizationId);
        grade.setCode("L1");
        grade.setName("Junior");
        grade.setOrdinal(1);
        grade.setModelSelectionMode(JobGrade.ModelSelectionMode.PRIORITY);
        grade.setStatus(JobGrade.Status.ACTIVE);
        grade = jobGradeService.createGrade(grade);

        List<GradeModel> models = List.of(
                GradeModel.builder().gradeId(grade.getId()).modelProfileId(profile1Id).priority(1).enabled(true).build(),
                GradeModel.builder().gradeId(grade.getId()).modelProfileId(profile2Id).priority(2).enabled(true).build()
        );

        jobGradeService.setGradeModels(grade.getId(), models);

        List<GradeModel> retrieved = jobGradeService.getGradeModels(grade.getId());
        assertEquals(2, retrieved.size());
        assertEquals(profile1Id, retrieved.get(0).getModelProfileId());
        assertEquals(1, retrieved.get(0).getPriority());
        assertEquals(profile2Id, retrieved.get(1).getModelProfileId());
        assertEquals(2, retrieved.get(1).getPriority());
    }

    @Test
    public void testDeleteGradeWithEmployeesFails() {
        JobGrade grade = new JobGrade();
        grade.setOrganizationId(organizationId);
        grade.setCode("L1");
        grade.setName("Junior");
        grade.setOrdinal(1);
        grade.setModelSelectionMode(JobGrade.ModelSelectionMode.PRIORITY);
        grade.setStatus(JobGrade.Status.ACTIVE);
        grade = jobGradeService.createGrade(grade);

        // Create employee with this grade
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@test.com");
        user.setName("Test User");
        user = userRepository.save(user);

        Employee employee = new Employee();
        employee.setUser(user);
        employee.setName("Test Employee");
        employee.setRole(Role.DEVELOPER);
        employee.setGradeId(grade.getId());
        employee.setStatus(Status.IDLE);
        employeeRepository.save(employee);

        final UUID gradeIdForDelete = grade.getId();
        assertThrows(IllegalStateException.class, () -> jobGradeService.deleteGrade(gradeIdForDelete));
    }

    @Test
    public void testDeleteGradeWithModelsFails() {
        JobGrade grade = new JobGrade();
        grade.setOrganizationId(organizationId);
        grade.setCode("L1");
        grade.setName("Junior");
        grade.setOrdinal(1);
        grade.setModelSelectionMode(JobGrade.ModelSelectionMode.PRIORITY);
        grade.setStatus(JobGrade.Status.ACTIVE);
        grade = jobGradeService.createGrade(grade);
        final UUID gradeIdForDelete = grade.getId();

        // Add grade model
        GradeModel gm = new GradeModel();
        gm.setGradeId(gradeIdForDelete);
        gm.setModelProfileId(profile1Id);
        gm.setPriority(1);
        gm.setEnabled(true);
        gradeModelRepository.save(gm);

        assertThrows(IllegalStateException.class, () -> jobGradeService.deleteGrade(gradeIdForDelete));
    }

    @Test
    public void testUpdateGrade() {
        JobGrade grade = new JobGrade();
        grade.setOrganizationId(organizationId);
        grade.setCode("L1");
        grade.setName("Junior");
        grade.setOrdinal(1);
        grade.setModelSelectionMode(JobGrade.ModelSelectionMode.PRIORITY);
        grade.setStatus(JobGrade.Status.ACTIVE);
        grade = jobGradeService.createGrade(grade);

        JobGrade updated = new JobGrade();
        updated.setCode("L1-UPDATED");
        updated.setName("Junior Updated");
        updated.setOrdinal(1);
        updated.setModelSelectionMode(JobGrade.ModelSelectionMode.TASK_BASED);
        updated.setStatus(JobGrade.Status.ACTIVE);

        JobGrade result = jobGradeService.updateGrade(grade.getId(), updated);
        assertEquals("L1-UPDATED", result.getCode());
        assertEquals("Junior Updated", result.getName());
        assertEquals(JobGrade.ModelSelectionMode.TASK_BASED, result.getModelSelectionMode());
    }
}