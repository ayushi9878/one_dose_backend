package com.careflow.integration;

import com.careflow.careplan.dto.DischargeRequest;
import com.careflow.common.enums.CarePlanType;
import com.careflow.common.enums.MedicationFrequency;
import com.careflow.escalation.dto.AssignEscalationRequest;
import com.careflow.escalation.dto.ResolveEscalationRequest;
import com.careflow.followup.dto.PatientResponseRequest;
import com.careflow.medication.dto.MedicationRequest;
import com.careflow.patient.dto.PatientRequest;
import com.careflow.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the complete continuity-care workflow through the HTTP API:
 * register a patient, prescribe medication, discharge, respond to a follow-up,
 * and confirm the resulting risk signal, escalation and audit trail.
 */
@DisplayName("End-to-end care workflow")
class CareWorkflowIntegrationTest extends IntegrationTestBase {

    private String uniqueMrn() {
        return "MRN-" + System.nanoTime() % 1_000_000;
    }

    private long createPatient(String token, String mrn) throws Exception {
        String body = mockMvc.perform(post("/api/patients")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(new PatientRequest(
                                mrn, "Rina", "Mehta", LocalDate.of(1978, 4, 12),
                                "+91-98200-11223", "rina.mehta@example.com",
                                "Congestive heart failure", careManager.getId()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asLong();
    }

    private void addMedication(String token, long patientId) throws Exception {
        mockMvc.perform(post("/api/patients/" + patientId + "/medications")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(new MedicationRequest(
                                "Metoprolol", "25 mg", MedicationFrequency.TWICE_DAILY,
                                LocalDate.now().minusDays(10), null, "Take with food.", true))))
                .andExpect(status().isCreated());
    }

    private JsonNode discharge(String token, long patientId, CarePlanType planType) throws Exception {
        String body = mockMvc.perform(post("/api/patients/" + patientId + "/discharge")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(new DischargeRequest(LocalDate.now().minusDays(1), planType))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    @DisplayName("discharging a patient generates the plan's follow-up schedule")
    void dischargeGeneratesFollowUps() throws Exception {
        String token = careManagerToken();
        long patientId = createPatient(token, uniqueMrn());

        JsonNode result = discharge(token, patientId, CarePlanType.STANDARD);

        assertThat(result.get("followUpsCreated").asInt()).isEqualTo(4);
        assertThat(result.get("carePlan").get("planType").asText()).isEqualTo("STANDARD");

        mockMvc.perform(get("/api/patients/" + patientId + "/follow-ups")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].dayOffset").value(1))
                .andExpect(jsonPath("$[3].dayOffset").value(30));
    }

    @Test
    @DisplayName("a HIGH_RISK discharge schedules the extra day 3 touchpoint")
    void highRiskDischargeAddsDayThree() throws Exception {
        String token = careManagerToken();
        long patientId = createPatient(token, uniqueMrn());

        JsonNode result = discharge(token, patientId, CarePlanType.HIGH_RISK);

        assertThat(result.get("followUpsCreated").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("a patient cannot be discharged twice")
    void doubleDischargeIsRejected() throws Exception {
        String token = careManagerToken();
        long patientId = createPatient(token, uniqueMrn());
        discharge(token, patientId, CarePlanType.STANDARD);

        mockMvc.perform(post("/api/patients/" + patientId + "/discharge")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(new DischargeRequest(
                                LocalDate.now().minusDays(1), CarePlanType.STANDARD))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a concerning response creates a HIGH risk signal and opens an escalation")
    void concerningResponseTriggersEscalation() throws Exception {
        String token = careManagerToken();
        long patientId = createPatient(token, uniqueMrn());
        addMedication(token, patientId);

        JsonNode discharge = discharge(token, patientId, CarePlanType.STANDARD);
        long followUpId = discharge.get("followUps").get(0).get("id").asLong();

        String body = mockMvc.perform(post("/api/follow-ups/" + followUpId + "/response")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(new PatientResponseRequest(
                                false, 3, true, List.of("dizziness"), true,
                                "Patient reported feeling dizzy."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.riskEvaluation.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.riskEvaluation.requiresHumanReview").value(true))
                .andExpect(jsonPath("$.escalation.severity").value("HIGH"))
                .andExpect(jsonPath("$.escalation.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        JsonNode result = objectMapper.readTree(body);
        assertThat(result.get("riskEvaluation").get("signals").toString())
                .contains("SYMPTOMS_REPORTED")
                .contains("MULTIPLE_MISSED_DOSES");
        assertThat(result.get("adherence").get("expectedDoses").asInt()).isPositive();
    }

    @Test
    @DisplayName("a clean response records adherence without opening a case")
    void cleanResponseCreatesNoEscalation() throws Exception {
        String token = careManagerToken();
        long patientId = createPatient(token, uniqueMrn());
        addMedication(token, patientId);

        JsonNode discharge = discharge(token, patientId, CarePlanType.STANDARD);
        long followUpId = discharge.get("followUps").get(0).get("id").asLong();

        mockMvc.perform(post("/api/follow-ups/" + followUpId + "/response")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(new PatientResponseRequest(
                                true, 0, false, List.of(), false, "Doing well."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.riskEvaluation.riskLevel").value("NONE"))
                .andExpect(jsonPath("$.riskEvaluation.requiresHumanReview").value(false))
                .andExpect(jsonPath("$.escalation").doesNotExist());
    }

    @Test
    @DisplayName("the same follow-up cannot be answered twice")
    void duplicateResponseIsRejected() throws Exception {
        String token = careManagerToken();
        long patientId = createPatient(token, uniqueMrn());
        addMedication(token, patientId);

        JsonNode discharge = discharge(token, patientId, CarePlanType.STANDARD);
        long followUpId = discharge.get("followUps").get(0).get("id").asLong();

        String payload = json(new PatientResponseRequest(
                true, 0, false, List.of(), false, "First answer."));

        mockMvc.perform(post("/api/follow-ups/" + followUpId + "/response")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/follow-ups/" + followUpId + "/response")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON).content(payload))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("an escalation can be assigned, reviewed and resolved with notes")
    void escalationCanBeWorkedToResolution() throws Exception {
        String token = careManagerToken();
        long patientId = createPatient(token, uniqueMrn());
        addMedication(token, patientId);

        JsonNode discharge = discharge(token, patientId, CarePlanType.STANDARD);
        long followUpId = discharge.get("followUps").get(0).get("id").asLong();

        String responseBody = mockMvc.perform(post("/api/follow-ups/" + followUpId + "/response")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(new PatientResponseRequest(
                                false, 4, true, List.of("dizziness"), true, "Feeling unwell."))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long escalationId = objectMapper.readTree(responseBody)
                .get("escalation").get("id").asLong();

        mockMvc.perform(post("/api/escalations/" + escalationId + "/assign")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(new AssignEscalationRequest(careManager.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        mockMvc.perform(post("/api/escalations/" + escalationId + "/review")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));

        mockMvc.perform(post("/api/escalations/" + escalationId + "/resolve")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(new ResolveEscalationRequest(
                                "Contacted the patient; refill arranged and dosing reviewed."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionNotes").isNotEmpty())
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
    }

    @Test
    @DisplayName("resolving without notes is rejected")
    void resolutionRequiresNotes() throws Exception {
        String token = careManagerToken();
        long patientId = createPatient(token, uniqueMrn());
        addMedication(token, patientId);

        JsonNode discharge = discharge(token, patientId, CarePlanType.STANDARD);
        long followUpId = discharge.get("followUps").get(0).get("id").asLong();

        String responseBody = mockMvc.perform(post("/api/follow-ups/" + followUpId + "/response")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(new PatientResponseRequest(
                                false, 4, true, List.of("dizziness"), true, null))))
                .andReturn().getResponse().getContentAsString();

        long escalationId = objectMapper.readTree(responseBody).get("escalation").get("id").asLong();

        mockMvc.perform(post("/api/escalations/" + escalationId + "/resolve")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(new ResolveEscalationRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.resolutionNotes").isNotEmpty());
    }

    @Test
    @DisplayName("every workflow step is written to the patient's audit trail")
    void workflowIsFullyAudited() throws Exception {
        String token = careManagerToken();
        long patientId = createPatient(token, uniqueMrn());
        addMedication(token, patientId);

        JsonNode discharge = discharge(token, patientId, CarePlanType.STANDARD);
        long followUpId = discharge.get("followUps").get(0).get("id").asLong();

        mockMvc.perform(post("/api/follow-ups/" + followUpId + "/response")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(new PatientResponseRequest(
                                false, 3, true, List.of("dizziness"), true, null))))
                .andExpect(status().isCreated());

        String auditBody = mockMvc.perform(get("/api/patients/" + patientId + "/audit-log")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(auditBody)
                .contains("PATIENT_CREATED")
                .contains("MEDICATION_ADDED")
                .contains("CARE_PLAN_CREATED")
                .contains("PATIENT_DISCHARGED")
                .contains("FOLLOW_UP_CREATED")
                .contains("RESPONSE_RECEIVED")
                .contains("RISK_SIGNAL_CREATED")
                .contains("ESCALATION_CREATED");
    }

    @Test
    @DisplayName("adherence is calculated by the backend from recorded doses")
    void adherenceIsCalculatedServerSide() throws Exception {
        String token = careManagerToken();
        long patientId = createPatient(token, uniqueMrn());
        addMedication(token, patientId);

        JsonNode discharge = discharge(token, patientId, CarePlanType.STANDARD);
        long followUpId = discharge.get("followUps").get(0).get("id").asLong();

        mockMvc.perform(post("/api/follow-ups/" + followUpId + "/response")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content(json(new PatientResponseRequest(
                                true, 1, false, List.of(), false, null))))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/patients/" + patientId + "/adherence")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode adherence = objectMapper.readTree(body);
        int expected = adherence.get("expectedDoses").asInt();
        int taken = adherence.get("takenDoses").asInt();
        double percentage = adherence.get("adherencePercentage").asDouble();

        assertThat(expected).isPositive();
        assertThat(percentage).isEqualTo(
                Math.round(taken * 100.0 / expected * 100.0) / 100.0);
    }
}
