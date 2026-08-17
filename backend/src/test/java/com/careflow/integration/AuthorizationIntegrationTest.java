package com.careflow.integration;

import com.careflow.common.enums.UserRole;
import com.careflow.patient.Patient;
import com.careflow.patient.PatientRepository;
import com.careflow.patient.dto.PatientRequest;
import com.careflow.support.IntegrationTestBase;
import com.careflow.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Role-based authorization")
class AuthorizationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private PatientRepository patientRepository;

    private Patient patientAssignedTo(User owner, String mrn) {
        return patientRepository.save(Patient.builder()
                .medicalRecordNumber(mrn)
                .firstName("Case")
                .lastName("Subject")
                .dateOfBirth(LocalDate.of(1980, 1, 1))
                .careManager(owner)
                .active(true)
                .build());
    }

    @Test
    @DisplayName("a care manager cannot open a patient outside their caseload")
    void careManagerCannotReachOtherCaseloads() throws Exception {
        User otherManager = findOrCreate(
                "other.manager@test.careflow", "Other Manager", UserRole.CARE_MANAGER);
        Patient foreignPatient = patientAssignedTo(otherManager, "MRN-FOREIGN-1");

        mockMvc.perform(get("/api/patients/" + foreignPatient.getId())
                        .header(AUTHORIZATION, bearer(careManagerToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a care manager can open a patient on their own caseload")
    void careManagerCanReachOwnCaseload() throws Exception {
        Patient ownPatient = patientAssignedTo(careManager, "MRN-OWN-1");

        mockMvc.perform(get("/api/patients/" + ownPatient.getId())
                        .header(AUTHORIZATION, bearer(careManagerToken())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an admin can open any patient")
    void adminSeesEveryPatient() throws Exception {
        User otherManager = findOrCreate(
                "third.manager@test.careflow", "Third Manager", UserRole.CARE_MANAGER);
        Patient foreignPatient = patientAssignedTo(otherManager, "MRN-FOREIGN-2");

        mockMvc.perform(get("/api/patients/" + foreignPatient.getId())
                        .header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a patient account cannot browse the patient roster")
    void patientRoleCannotListRoster() throws Exception {
        User portalUser = findOrCreate("portal@test.careflow", "Portal User", UserRole.PATIENT);

        mockMvc.perform(get("/api/patients").header(AUTHORIZATION, bearer(tokenFor(portalUser.getEmail()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a patient account cannot access the escalation queue")
    void patientRoleCannotSeeEscalations() throws Exception {
        User portalUser = findOrCreate("portal2@test.careflow", "Portal Two", UserRole.PATIENT);

        mockMvc.perform(get("/api/escalations")
                        .header(AUTHORIZATION, bearer(tokenFor(portalUser.getEmail()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("only an admin may deactivate a patient")
    void onlyAdminCanDeactivatePatients() throws Exception {
        Patient patient = patientAssignedTo(careManager, "MRN-DEACTIVATE-1");

        mockMvc.perform(delete("/api/patients/" + patient.getId())
                        .header(AUTHORIZATION, bearer(careManagerToken())))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/patients/" + patient.getId())
                        .header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("a patient account cannot register new patients")
    void patientRoleCannotCreatePatients() throws Exception {
        User portalUser = findOrCreate("portal3@test.careflow", "Portal Three", UserRole.PATIENT);

        mockMvc.perform(post("/api/patients")
                        .header(AUTHORIZATION, bearer(tokenFor(portalUser.getEmail())))
                        .contentType(APPLICATION_JSON)
                        .content(json(new PatientRequest(
                                "MRN-BLOCKED-1", "Blocked", "Patient",
                                LocalDate.of(1990, 1, 1), null, null, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the dashboard is closed to patient accounts")
    void patientRoleCannotSeeDashboard() throws Exception {
        User portalUser = findOrCreate("portal4@test.careflow", "Portal Four", UserRole.PATIENT);

        mockMvc.perform(get("/api/dashboard/summary")
                        .header(AUTHORIZATION, bearer(tokenFor(portalUser.getEmail()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the public version endpoint needs no authentication")
    void versionEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/system/version"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the actuator health endpoint is reachable for deployment checks")
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("actuator endpoints other than health are restricted to admins")
    void nonHealthActuatorEndpointsAreAdminOnly() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/info").header(AUTHORIZATION, bearer(careManagerToken())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/info").header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk());
    }
}
