package no.nav.amt.deltaker.testdata

import java.time.LocalDate
import java.util.UUID

const val MIN_DAGER_PER_UKE = 1
const val MAX_DAGER_PER_UKE = 5
const val MIN_DELTAKELSESPROSENT = 1
const val MAX_DELTAKELSESPROSENT = 100

data class OpprettTestDeltakelseRequest(
    val personident: String,
    val deltakerlisteId: UUID,
    val startdato: LocalDate,
    val deltakelsesprosent: Int,
    val dagerPerUke: Int?,
) {
    fun valider() {
        dagerPerUke?.let {
            require(it in MIN_DAGER_PER_UKE..MAX_DAGER_PER_UKE) {
                "Dager per uke kan ikke være mindre enn $MIN_DAGER_PER_UKE eller større enn $MAX_DAGER_PER_UKE"
            }
        }
        require(deltakelsesprosent in MIN_DELTAKELSESPROSENT..MAX_DELTAKELSESPROSENT) {
            "Deltakelsesprosent kan ikke være mindre enn $MIN_DELTAKELSESPROSENT eller større enn $MAX_DELTAKELSESPROSENT"
        }
    }
}
