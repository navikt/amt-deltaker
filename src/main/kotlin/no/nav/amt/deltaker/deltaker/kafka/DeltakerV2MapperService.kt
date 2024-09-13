package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerHistorikk
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import java.time.LocalDate
import java.util.UUID

class DeltakerV2MapperService(
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val deltakerHistorikkService: DeltakerHistorikkService,
) {
    suspend fun tilDeltakerV2Dto(deltaker: Deltaker, forcedUpdate: Boolean? = false): DeltakerV2Dto {
        val deltakerhistorikk = deltakerHistorikkService.getForDeltaker(deltaker.id)

        val sisteEndring = getSisteEndring(deltakerhistorikk)

        return DeltakerV2Dto(
            id = deltaker.id,
            deltakerlisteId = deltaker.deltakerliste.id,
            personalia = DeltakerV2Dto.DeltakerPersonaliaDto(
                personId = deltaker.navBruker.personId,
                personident = deltaker.navBruker.personident,
                navn = DeltakerV2Dto.Navn(
                    fornavn = deltaker.navBruker.fornavn,
                    mellomnavn = deltaker.navBruker.mellomnavn,
                    etternavn = deltaker.navBruker.etternavn,
                ),
                kontaktinformasjon = DeltakerV2Dto.DeltakerKontaktinformasjonDto(
                    telefonnummer = deltaker.navBruker.telefon,
                    epost = deltaker.navBruker.epost,
                ),
                skjermet = deltaker.navBruker.erSkjermet,
                adresse = deltaker.navBruker.adresse,
                adressebeskyttelse = deltaker.navBruker.adressebeskyttelse,
            ),
            status = DeltakerV2Dto.DeltakerStatusDto(
                id = deltaker.status.id,
                type = deltaker.status.type,
                aarsak = deltaker.status.aarsak?.type,
                aarsaksbeskrivelse = deltaker.status.aarsak?.beskrivelse,
                gyldigFra = deltaker.status.gyldigFra,
                opprettetDato = deltaker.status.opprettet,
            ),
            dagerPerUke = deltaker.dagerPerUke,
            prosentStilling = deltaker.deltakelsesprosent?.toDouble(),
            oppstartsdato = deltaker.startdato,
            sluttdato = deltaker.sluttdato,
            innsoktDato = deltakerHistorikkService.getInnsoktDato(deltakerhistorikk)
                ?: throw IllegalStateException("Skal ikke produsere deltaker som mangler vedtak til topic"),
            forsteVedtakFattet = getForsteVedtakFattet(deltakerhistorikk),
            bestillingTekst = deltaker.bakgrunnsinformasjon,
            navKontor = deltaker.navBruker.navEnhetId?.let { navEnhetService.hentEllerOpprettNavEnhet(it) }?.navn,
            navVeileder = deltaker.navBruker.navVeilederId?.let { navAnsattService.hentEllerOpprettNavAnsatt(it) }
                ?.toDeltakerNavVeilederDto(),
            deltarPaKurs = deltaker.deltarPaKurs(),
            kilde = deltaker.kilde,
            innhold = deltaker.deltakelsesinnhold,
            historikk = deltakerhistorikk,
            sistEndret = deltaker.sistEndret,
            sistEndretAv = getSistEndretAv(sisteEndring),
            sistEndretAvEnhet = getSistEndretAvEnhet(sisteEndring),
            forcedUpdate = forcedUpdate,
        )
    }

    private fun getForsteVedtakFattet(deltakerhistorikk: List<DeltakerHistorikk>): LocalDate? {
        val vedtak = deltakerhistorikk.filterIsInstance<DeltakerHistorikk.Vedtak>().map { it.vedtak }
        val forsteVedtak = vedtak.minByOrNull { it.opprettet }
            ?: throw IllegalStateException("Skal ikke produsere deltaker som mangler vedtak til topic")

        return forsteVedtak.fattet?.toLocalDate()
    }

    private fun getSisteEndring(deltakerhistorikk: List<DeltakerHistorikk>): DeltakerHistorikk {
        return deltakerhistorikk.filterNot { it is DeltakerHistorikk.Forslag || it is DeltakerHistorikk.EndringFraArrangor }.firstOrNull()
            ?: throw IllegalStateException("Deltaker må ha minst et vedtak for å produseres til topic")
    }

    private fun getSistEndretAv(deltakerhistorikk: DeltakerHistorikk): UUID {
        return when (deltakerhistorikk) {
            is DeltakerHistorikk.Vedtak -> deltakerhistorikk.vedtak.sistEndretAv
            is DeltakerHistorikk.Endring -> deltakerhistorikk.endring.endretAv
            is DeltakerHistorikk.Forslag,
            is DeltakerHistorikk.EndringFraArrangor,
            -> throw IllegalStateException("Siste endring kan ikke være et forslag eller endring fra arrangør")
        }
    }

    private fun getSistEndretAvEnhet(deltakerhistorikk: DeltakerHistorikk): UUID {
        return when (deltakerhistorikk) {
            is DeltakerHistorikk.Vedtak -> deltakerhistorikk.vedtak.sistEndretAvEnhet
            is DeltakerHistorikk.Endring -> deltakerhistorikk.endring.endretAvEnhet
            is DeltakerHistorikk.Forslag,
            is DeltakerHistorikk.EndringFraArrangor,
            -> throw IllegalStateException("Siste endring kan ikke være et forslag eller endring fra arrangør")
        }
    }
}
