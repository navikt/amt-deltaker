package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navansatt.navenhet.NavEnhetService

class DeltakerV2MapperService(
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val deltakerHistorikkService: DeltakerHistorikkService,
) {
    suspend fun tilDeltakerV2Dto(deltaker: Deltaker): DeltakerV2Dto {
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
            innsoktDato = deltaker.opprettet.toLocalDate(),
            bestillingTekst = deltaker.bakgrunnsinformasjon,
            navKontor = deltaker.navBruker.navEnhetId?.let { navEnhetService.hentEllerOpprettNavEnhet(it) }?.enhetsnummer,
            navVeileder = deltaker.navBruker.navVeilederId?.let { navAnsattService.hentEllerOpprettNavAnsatt(it) }
                ?.toDeltakerNavVeilederDto(),
            deltarPaKurs = deltaker.deltarPaKurs(),
            kilde = DeltakerV2Dto.Kilde.KOMET,
            innhold = deltaker.innhold,
            historikk = deltakerHistorikkService.getForDeltaker(deltaker.id),
            sistEndret = deltaker.sistEndret,
            sistEndretAv = deltaker.sistEndretAv,
            sistEndretAvEnhet = deltaker.sistEndretAvEnhet,
            opprettet = deltaker.opprettet,
        )
    }
}
