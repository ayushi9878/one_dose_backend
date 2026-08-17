package com.careflow.patient.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Payload for creating or updating a patient record.")
public record PatientRequest(

        @Schema(example = "MRN-10024", description = "Unique medical record number.")
        @NotBlank(message = "Medical record number is required.")
        @Size(max = 32)
        @Pattern(regexp = "^[A-Za-z0-9\\-]+$",
                message = "Medical record number may contain letters, digits and hyphens only.")
        String medicalRecordNumber,

        @Schema(example = "Rina")
        @NotBlank(message = "First name is required.")
        @Size(max = 80)
        String firstName,

        @Schema(example = "Mehta")
        @NotBlank(message = "Last name is required.")
        @Size(max = 80)
        String lastName,

        @Schema(example = "1976-04-12")
        @NotNull(message = "Date of birth is required.")
        @Past(message = "Date of birth must be in the past.")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate dateOfBirth,

        @Schema(example = "+91-98200-11223")
        @Size(max = 20)
        String phone,

        @Email(message = "Email must be a valid address.")
        @Size(max = 190)
        String email,

        @Schema(example = "Congestive heart failure")
        @Size(max = 200)
        String primaryCondition,

        @Schema(description = "Care manager responsible for this patient.")
        Long careManagerId) {
}
