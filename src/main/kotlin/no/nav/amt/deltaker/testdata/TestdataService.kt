package no.nav.amt.deltaker.testdata

import kotlinx.coroutines.delay
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltaker.PameldingService
import no.nav.amt.deltaker.deltaker.api.model.UtkastRequest
import no.nav.amt.deltaker.deltaker.forslag.kafka.ArrangorMeldingProducer
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltakerliste.Deltakerliste
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.lib.models.arrangor.melding.EndringFraArrangor
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.Innhold
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Innholdselement
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakstype
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

const val TESTVEILEDER = "Z990098"
const val TESTENHET = "0314"

// Plutselig Lagsport
const val TESTARRANGORANSATT = "fff9a665-cbde-4dbc-9ef9-deb8681a0d6f"

class TestdataService(
    private val pameldingService: PameldingService,
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val arrangorMeldingProducer: ArrangorMeldingProducer,
    private val deltakerService: DeltakerService,
) {
    suspend fun opprettDeltakelse(opprettTestDeltakelseRequest: OpprettTestDeltakelseRequest): Deltaker {
        val deltakerliste = deltakerlisteRepository.get(opprettTestDeltakelseRequest.deltakerlisteId).getOrThrow()
        val forventetSluttdato = opprettTestDeltakelseRequest.startdato.plusMonths(3)
        valider(
            startdato = opprettTestDeltakelseRequest.startdato,
            sluttdato = forventetSluttdato,
            deltakerliste = deltakerliste,
        )
        val kladd = pameldingService.opprettKladd(
            deltakerlisteId = opprettTestDeltakelseRequest.deltakerlisteId,
            personident = opprettTestDeltakelseRequest.personident,
        )
        val deltakerId = kladd.id

        delay(10)

        val utkast = getUtkastRequest(deltakerliste, opprettTestDeltakelseRequest)
        pameldingService.upsertUtkast(
            deltakerId = deltakerId,
            utkast = utkast,
        )

        delay(100)

        val endringFraArrangor = getEndringFraArrangor(
            deltakerId = deltakerId,
            startdato = opprettTestDeltakelseRequest.startdato,
            sluttdato = forventetSluttdato,
        )
        arrangorMeldingProducer.produce(endringFraArrangor)

        delay(100)

        return deltakerService.get(deltakerId).getOrThrow()
    }

    private fun valider(
        startdato: LocalDate,
        sluttdato: LocalDate,
        deltakerliste: Deltakerliste,
    ) {
        if (deltakerliste.tiltakstype.tiltakskode != Tiltakstype.Tiltakskode.ARBEIDSFORBEREDENDE_TRENING) {
            throw IllegalArgumentException("Det er kun AFT som er støttet for testdeltakelser inntil videre")
        }
        if (startdato.isBefore(deltakerliste.startDato)) {
            throw IllegalArgumentException("Kan ikke sette startdato tidligere enn gjennomføringens startdato")
        }
        if (deltakerliste.sluttDato != null && sluttdato.isAfter(deltakerliste.sluttDato)) {
            throw IllegalArgumentException("Kan ikke sette sluttdato senere enn gjennomføringens sluttdato")
        }
    }

    fun getEndringFraArrangor(
        deltakerId: UUID,
        startdato: LocalDate,
        sluttdato: LocalDate,
    ): EndringFraArrangor = EndringFraArrangor(
        id = UUID.randomUUID(),
        deltakerId = deltakerId,
        opprettetAvArrangorAnsattId = UUID.fromString(TESTARRANGORANSATT),
        opprettet = LocalDateTime.now(),
        endring = EndringFraArrangor.LeggTilOppstartsdato(
            startdato = startdato,
            sluttdato = sluttdato,
        ),
    )

    private fun getUtkastRequest(deltakerliste: Deltakerliste, opprettTestDeltakelseRequest: OpprettTestDeltakelseRequest): UtkastRequest {
        return UtkastRequest(
            deltakelsesinnhold = getInnhold(deltakerliste),
            bakgrunnsinformasjon = null,
            deltakelsesprosent = opprettTestDeltakelseRequest.deltakelsesprosent.toFloat(),
            dagerPerUke = opprettTestDeltakelseRequest.dagerPerUke?.toFloat(),
            endretAv = TESTVEILEDER,
            endretAvEnhet = TESTENHET,
            godkjentAvNav = true,
        )
    }

    private fun getInnhold(deltakerliste: Deltakerliste): Deltakelsesinnhold {
        val innhold = deltakerliste.tiltakstype.innhold
        val valgtInnhold = innhold?.innholdselementer?.firstOrNull()?.toInnhold()
        return Deltakelsesinnhold(
            ledetekst = innhold?.ledetekst,
            innhold = valgtInnhold?.let { listOf(it) } ?: emptyList(),
        )
    }

    private fun Innholdselement.toInnhold(valgt: Boolean = true, beskrivelse: String? = null) = Innhold(
        tekst = tekst,
        innholdskode = innholdskode,
        valgt = valgt,
        beskrivelse = beskrivelse,
    )
}
