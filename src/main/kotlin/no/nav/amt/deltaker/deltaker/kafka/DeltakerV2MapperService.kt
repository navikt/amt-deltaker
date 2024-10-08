package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.Kilde
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import java.time.LocalDate
import java.util.UUID

class DeltakerV2MapperService(
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val deltakerHistorikkService: DeltakerHistorikkService,
) {
    suspend fun tilDeltakerV2Dto(deltaker: Deltaker, forcedUpdate: Boolean? = false): DeltakerV2Dto {
        val deltakerhistorikk = deltakerHistorikkService.getForDeltaker(deltaker.id)

        val sisteEndring = deltakerhistorikk.getSisteEndring()

        if (deltaker.kilde == Kilde.KOMET && sisteEndring == null) {
            throw IllegalStateException("Deltaker med kilde ${Kilde.KOMET} må ha minst et vedtak for å produseres til topic")
        }

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
            navKontor = deltaker.navBruker.navEnhetId
                ?.let { navEnhetService.hentEllerOpprettNavEnhet(it) }
                ?.navn,
            navVeileder = deltaker.navBruker.navVeilederId
                ?.let { navAnsattService.hentEllerOpprettNavAnsatt(it) }
                ?.toDeltakerNavVeilederDto(),
            deltarPaKurs = deltaker.deltarPaKurs(),
            kilde = deltaker.kilde,
            innhold = deltaker.deltakelsesinnhold,
            historikk = deltakerhistorikk,
            sistEndret = deltaker.sistEndret,
            sistEndretAv = sisteEndring?.let { getSistEndretAv(it) },
            sistEndretAvEnhet = sisteEndring?.let { getSistEndretAvEnhet(it) },
            forcedUpdate = forcedUpdate,
        )
    }

    private fun getForsteVedtakFattet(deltakerhistorikk: List<DeltakerHistorikk>): LocalDate? {
        deltakerHistorikkService.getInnsoktDatoFraImportertDeltaker(deltakerhistorikk)?.let { return it }

        val vedtak = deltakerhistorikk.filterIsInstance<DeltakerHistorikk.Vedtak>().map { it.vedtak }
        val forsteVedtak = vedtak.minByOrNull { it.opprettet }
            ?: throw IllegalStateException("Skal ikke produsere deltaker som mangler vedtak til topic")

        return forsteVedtak.fattet?.toLocalDate()
    }

    private fun getSistEndretAv(deltakerhistorikk: DeltakerHistorikk): UUID = when (deltakerhistorikk) {
        is DeltakerHistorikk.Vedtak -> deltakerhistorikk.vedtak.sistEndretAv
        is DeltakerHistorikk.Endring -> deltakerhistorikk.endring.endretAv
        is DeltakerHistorikk.Forslag,
        is DeltakerHistorikk.EndringFraArrangor,
        is DeltakerHistorikk.ImportertFraArena,
        -> throw IllegalStateException("Siste endring kan ikke være et forslag eller endring fra arrangør")
    }

    private fun getSistEndretAvEnhet(deltakerhistorikk: DeltakerHistorikk): UUID = when (deltakerhistorikk) {
        is DeltakerHistorikk.Vedtak -> deltakerhistorikk.vedtak.sistEndretAvEnhet
        is DeltakerHistorikk.Endring -> deltakerhistorikk.endring.endretAvEnhet
        is DeltakerHistorikk.Forslag,
        is DeltakerHistorikk.EndringFraArrangor,
        is DeltakerHistorikk.ImportertFraArena,
        -> throw IllegalStateException("Siste endring kan ikke være et forslag eller endring fra arrangør")
    }
}

private fun List<DeltakerHistorikk>.getSisteEndring() = this.firstOrNull {
    it is DeltakerHistorikk.Vedtak || it is DeltakerHistorikk.Endring
}
