package com.careflow.adherence;

/** Aggregate projection over a patient's adherence events. */
public interface AdherenceTotals {

    Long getExpectedDoses();

    Long getTakenDoses();

    Long getMissedDoses();
}
