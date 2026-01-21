package no.nav.amt.deltaker.deltaker.endring

import io.ktor.server.plugins.requestvalidation.ValidationResult
import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.api.deltaker.toDeltakerEndringEndring
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.lib.models.deltaker.DeltakerEndring
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengde
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.models.deltaker.internalapis.deltaker.request.EndringRequest
import java.time.LocalDateTime

enum class UgyldigDeltakerEndring {
    AVSLUTT_DELTAKELSE_INGEN_ENDRING,
    ENDRE_DELTAKELSESMENGDE_INGEN_ENDRING,
    ENDRE_AVSLUTNING_INGEN_ENDRING,
    AVBRYT_DELTAKELSE_INGEN_ENDRING,
    ENDRE_BAKGRUNNSINFORMASJON_INGEN_ENDRING,
    ENDRE_INNHOLD_INGEN_ENDRING,
    ENDRE_SLUTTAARSAK_INGEN_ENDRING,
    ENDRE_SLUTTDATO_INGEN_ENDRING,
    ENDRE_STARTDATO_INGEN_ENDRING,
    FJERN_OPPSTARTSDATO_INGEN_ENDRING,
    FORLENG_DELTAKELSE_INGEN_ENDRING,
    SETT_IKKE_AKTUELL_INGEN_ENDRING,
    REAKTIVER_DELTAKELSE_INGEN_ENDRING,
}

class DeltakerEndringValidator(
    private val deltaker: Deltaker,
    private val deltakerHistorikkService: DeltakerHistorikkService,
) {
    fun validerRequest(request: EndringRequest): ValidationResult = when (val endring = request.toDeltakerEndringEndring()) {
        is DeltakerEndring.Endring.AvsluttDeltakelse -> kanAvslutteDeltakelse(endring)
        is DeltakerEndring.Endring.EndreDeltakelsesmengde -> kanEndreDeltakelsesmengde(endring)
        is DeltakerEndring.Endring.AvbrytDeltakelse -> kanAvbryteDeltakelse(endring)
        is DeltakerEndring.Endring.EndreAvslutning -> kanEndreAvslutning(endring)
        is DeltakerEndring.Endring.EndreBakgrunnsinformasjon -> kanEndreBakgrunnsinformasjon(endring)
        is DeltakerEndring.Endring.EndreInnhold -> kanEndreInnhold(endring)
        is DeltakerEndring.Endring.EndreSluttarsak -> kanEndreSluttaarsak(endring)
        is DeltakerEndring.Endring.EndreSluttdato -> kanEndreSluttdato(endring)
        is DeltakerEndring.Endring.EndreStartdato -> kanEndreStartdato(endring)
        is DeltakerEndring.Endring.FjernOppstartsdato -> kanFjerneOppstartsdato()
        is DeltakerEndring.Endring.ForlengDeltakelse -> kanForlengeDeltakelse()
        is DeltakerEndring.Endring.IkkeAktuell -> kanSetteIkkeAktuell(endring)
        is DeltakerEndring.Endring.ReaktiverDeltakelse -> kanReaktivereDeltakelse()
    }

    fun kanAvslutteDeltakelse(endring: DeltakerEndring.Endring.AvsluttDeltakelse): ValidationResult {
        val erGyldigEndring = deltaker.status.type != DeltakerStatus.Type.HAR_SLUTTET ||
            endring.sluttdato != deltaker.sluttdato ||
            deltaker.status.aarsak != endring.aarsak?.toDeltakerStatusAarsak()
        return if (erGyldigEndring) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(UgyldigDeltakerEndring.AVSLUTT_DELTAKELSE_INGEN_ENDRING.name)
        }
    }

    fun kanEndreAvslutning(endring: DeltakerEndring.Endring.EndreAvslutning): ValidationResult {
        val erGyldigEndring = (deltaker.status.type == DeltakerStatus.Type.FULLFORT && endring.harFullfort == false) ||
            (deltaker.status.type == DeltakerStatus.Type.AVBRUTT && endring.harFullfort == true) ||
            deltaker.sluttdato != endring.sluttdato ||
            deltaker.status.aarsak != endring.aarsak?.toDeltakerStatusAarsak()
        return if (erGyldigEndring) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(UgyldigDeltakerEndring.ENDRE_AVSLUTNING_INGEN_ENDRING.name)
        }
    }

    fun kanAvbryteDeltakelse(endring: DeltakerEndring.Endring.AvbrytDeltakelse): ValidationResult {
        val erGyldigEndring = deltaker.status.type != DeltakerStatus.Type.AVBRUTT ||
            endring.sluttdato != deltaker.sluttdato ||
            deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak()

        return if (erGyldigEndring) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(UgyldigDeltakerEndring.AVBRYT_DELTAKELSE_INGEN_ENDRING.name)
        }
    }

    fun kanEndreBakgrunnsinformasjon(endring: DeltakerEndring.Endring.EndreBakgrunnsinformasjon): ValidationResult {
        val erGyldigEndring = deltaker.bakgrunnsinformasjon != endring.bakgrunnsinformasjon
        return if (erGyldigEndring) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(UgyldigDeltakerEndring.ENDRE_BAKGRUNNSINFORMASJON_INGEN_ENDRING.name)
        }
    }

    fun kanEndreDeltakelsesmengde(endring: DeltakerEndring.Endring.EndreDeltakelsesmengde): ValidationResult {
        val deltakelsesmengder = deltakerHistorikkService.getForDeltaker(deltaker.id).toDeltakelsesmengder()
        val nyDeltakelsesmengde = endring.toDeltakelsesmengde(LocalDateTime.now())

        val erGyldigEndring = deltakelsesmengder.validerNyDeltakelsesmengde(nyDeltakelsesmengde)

        return if (erGyldigEndring) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(UgyldigDeltakerEndring.ENDRE_DELTAKELSESMENGDE_INGEN_ENDRING.name)
        }
    }

    fun kanEndreInnhold(endring: DeltakerEndring.Endring.EndreInnhold): ValidationResult {
        val erGyldigEndring = deltaker.deltakelsesinnhold?.innhold != endring.innhold

        return if (erGyldigEndring) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(UgyldigDeltakerEndring.ENDRE_INNHOLD_INGEN_ENDRING.name)
        }
    }

    fun kanEndreSluttaarsak(endring: DeltakerEndring.Endring.EndreSluttarsak): ValidationResult {
        val erGyldigEndring = deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak()

        return if (erGyldigEndring) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(UgyldigDeltakerEndring.ENDRE_SLUTTAARSAK_INGEN_ENDRING.name)
        }
    }

    fun kanEndreSluttdato(endring: DeltakerEndring.Endring.EndreSluttdato): ValidationResult {
        val erGyldigEndring = deltaker.sluttdato != endring.sluttdato

        return if (erGyldigEndring) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(UgyldigDeltakerEndring.ENDRE_SLUTTDATO_INGEN_ENDRING.name)
        }
    }

    fun kanEndreStartdato(endring: DeltakerEndring.Endring.EndreStartdato): ValidationResult {
        val erGyldigEndring = deltaker.startdato != endring.startdato || deltaker.sluttdato != endring.sluttdato

        return if (erGyldigEndring) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(UgyldigDeltakerEndring.ENDRE_STARTDATO_INGEN_ENDRING.name)
        }
    }

    fun kanFjerneOppstartsdato(): ValidationResult {
        val erGyldigEndring = deltaker.startdato != null

        return if (erGyldigEndring) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(UgyldigDeltakerEndring.FJERN_OPPSTARTSDATO_INGEN_ENDRING.name)
        }
    }

    fun kanForlengeDeltakelse(): ValidationResult {
        val erGyldigEndring = deltaker.startdato != null

        return if (erGyldigEndring) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(UgyldigDeltakerEndring.FORLENG_DELTAKELSE_INGEN_ENDRING.name)
        }
    }

    fun kanSetteIkkeAktuell(endring: DeltakerEndring.Endring.IkkeAktuell): ValidationResult {
        val erGyldigEndring = deltaker.status.aarsak != endring.aarsak.toDeltakerStatusAarsak()

        return if (erGyldigEndring) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(UgyldigDeltakerEndring.SETT_IKKE_AKTUELL_INGEN_ENDRING.name)
        }
    }

    fun kanReaktivereDeltakelse(): ValidationResult {
        val erGyldigEndring = deltaker.status.type == DeltakerStatus.Type.IKKE_AKTUELL

        return if (erGyldigEndring) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(UgyldigDeltakerEndring.REAKTIVER_DELTAKELSE_INGEN_ENDRING.name)
        }
    }
}
