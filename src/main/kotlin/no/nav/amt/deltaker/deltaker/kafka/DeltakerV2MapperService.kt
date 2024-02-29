package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.DeltakerEndring
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
    suspend fun tilDeltakerV2Dto(deltaker: Deltaker): DeltakerV2Dto {
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
                type = deltaker.status.type,
                aarsak = deltaker.status.aarsak,
                gyldigFra = deltaker.status.gyldigFra,
                opprettetDato = deltaker.status.opprettet,
            ),
            dagerPerUke = deltaker.dagerPerUke,
            prosentStilling = deltaker.deltakelsesprosent?.toDouble(),
            oppstartsdato = deltaker.startdato,
            sluttdato = deltaker.sluttdato,
            innsoktDato = getInnsoktDato(deltakerhistorikk),
            bestillingTekst = deltaker.bakgrunnsinformasjon,
            navKontor = deltaker.navBruker.navEnhetId?.let { navEnhetService.hentEllerOpprettNavEnhet(it) }?.enhetsnummer,
            navVeileder = deltaker.navBruker.navVeilederId?.let { navAnsattService.hentEllerOpprettNavAnsatt(it) }
                ?.toDeltakerNavVeilederDto(),
            deltarPaKurs = deltaker.deltarPaKurs(),
            kilde = DeltakerV2Dto.Kilde.KOMET,
            innhold = deltaker.innhold,
            historikk = deltakerhistorikk,
            sistEndret = deltaker.sistEndret,
            sistEndretAv = getSistEndretAv(sisteEndring),
            sistEndretAvEnhet = getSistEndretAvEnhet(sisteEndring),
        )
    }

    private fun getInnsoktDato(deltakerhistorikk: List<DeltakerHistorikk>): LocalDate {
        val vedtak = deltakerhistorikk.filterIsInstance<DeltakerHistorikk.Vedtak>().map { it.vedtak }
        return vedtak.minByOrNull { it.opprettet }?.opprettet?.toLocalDate()
            ?: throw IllegalStateException("Skal ikke produsere deltaker som mangler vedtak til topic")
    }

    private fun getSisteEndring(deltakerhistorikk: List<DeltakerHistorikk>): DeltakerHistorikk {
        val vedtak = deltakerhistorikk.filterIsInstance<DeltakerHistorikk.Vedtak>().map { it.vedtak }
        val nyesteVedtak = vedtak.minByOrNull { it.sistEndret }
            ?: throw IllegalStateException("Skal ikke produsere deltaker som mangler vedtak til topic")

        val endringer = deltakerhistorikk.filterIsInstance<DeltakerEndring>()
        val nyesteEndring = endringer.minByOrNull { it.endret }

        return if (nyesteEndring == null || nyesteVedtak.sistEndret.isAfter(nyesteEndring.endret)) {
            DeltakerHistorikk.Vedtak(nyesteVedtak)
        } else {
            DeltakerHistorikk.Endring(nyesteEndring)
        }
    }

    private fun getSistEndretAv(deltakerhistorikk: DeltakerHistorikk): UUID {
        return when (deltakerhistorikk) {
            is DeltakerHistorikk.Vedtak -> deltakerhistorikk.vedtak.sistEndretAv
            is DeltakerHistorikk.Endring -> deltakerhistorikk.endring.endretAv
        }
    }

    private fun getSistEndretAvEnhet(deltakerhistorikk: DeltakerHistorikk): UUID {
        return when (deltakerhistorikk) {
            is DeltakerHistorikk.Vedtak -> deltakerhistorikk.vedtak.sistEndretAvEnhet
            is DeltakerHistorikk.Endring -> deltakerhistorikk.endring.endretAvEnhet
        }
    }
}
