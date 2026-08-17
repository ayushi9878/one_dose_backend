package com.careflow.adherence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdherenceEventRepository extends JpaRepository<AdherenceEvent, Long> {

    List<AdherenceEvent> findByPatientIdOrderByRecordedDateAsc(Long patientId);

    @Query("""
            SELECT COALESCE(SUM(a.expectedDoses), 0) AS expectedDoses,
                   COALESCE(SUM(a.takenDoses), 0)    AS takenDoses,
                   COALESCE(SUM(a.missedDoses), 0)   AS missedDoses
            FROM AdherenceEvent a
            WHERE a.patient.id = :patientId
            """)
    AdherenceTotals sumTotalsForPatient(@Param("patientId") Long patientId);

    @Query("""
            SELECT COALESCE(SUM(a.takenDoses), 0) * 100.0 / NULLIF(SUM(a.expectedDoses), 0)
            FROM AdherenceEvent a
            """)
    Double calculateOverallAdherencePercentage();

    /**
     * One adherence percentage per patient, for bucketing into dashboard bands.
     */
    @Query("""
            SELECT SUM(a.takenDoses) * 100.0 / SUM(a.expectedDoses)
            FROM AdherenceEvent a
            GROUP BY a.patient.id
            HAVING SUM(a.expectedDoses) > 0
            """)
    List<Double> findAdherencePercentagePerPatient();
}
