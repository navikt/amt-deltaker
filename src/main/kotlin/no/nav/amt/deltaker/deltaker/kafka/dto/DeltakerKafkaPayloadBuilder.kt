package no.nav.amt.deltaker.deltaker.kafka.dto

import no.nav.amt.deltaker.deltaker.DeltakerHistorikkService
import no.nav.amt.deltaker.deltaker.extensions.getInnsoktDato
import no.nav.amt.deltaker.deltaker.extensions.getInnsoktDatoFraImportertDeltaker
import no.nav.amt.deltaker.deltaker.extensions.getStatustekst
import no.nav.amt.deltaker.deltaker.extensions.getVisningsnavn
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.vurdering.Vurdering
import no.nav.amt.deltaker.deltaker.vurdering.VurderingRepository
import no.nav.amt.deltaker.navansatt.NavAnsattService
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.lib.models.arrangor.melding.Vurderingstype
import no.nav.amt.lib.models.deltaker.Deltakelsesinnhold
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerKafkaPayload
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerStatusDto
import no.nav.amt.lib.models.deltaker.Deltakerliste
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltaker.Kontaktinformasjon
import no.nav.amt.lib.models.deltaker.Navn
import no.nav.amt.lib.models.deltaker.Personalia
import no.nav.amt.lib.models.deltaker.SisteEndring
import no.nav.amt.lib.models.deltaker.deltakelsesmengde.toDeltakelsesmengder
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltak
import java.time.LocalDate
import java.util.UUID

data class DeltakerKafkaPayloadBuilder(
    private val navAnsattService: NavAnsattService,
    private val navEnhetService: NavEnhetService,
    private val deltakerHistorikkService: DeltakerHistorikkService,
    private val vurderingRepository: VurderingRepository,
) {
    fun buildDeltakerV1Record(deltaker: Deltaker): DeltakerV1Dto {
        val deltakerhistorikk = deltakerHistorikkService.getForDeltaker(deltaker.id)
        val innsoktDato = deltakerhistorikk.getInnsoktDato()
            ?: throw IllegalStateException("Skal ikke produsere deltaker som mangler vedtak til topic")

        return DeltakerV1Dto(
            id = deltaker.id,
            gjennomforingId = deltaker.deltakerliste.id,
            personIdent = deltaker.navBruker.personident,
            startDato = deltaker.startdato,
            sluttDato = deltaker.sluttdato,
            status = DeltakerV1Dto.DeltakerStatusDto(
                type = deltaker.status.type,
                statusTekst = deltaker.status.type.getStatustekst(),
                aarsak = deltaker.status.aarsak?.type,
                aarsakTekst = deltaker.status.aarsak?.let {
                    DeltakerStatus
                        .Aarsak(type = it.type, beskrivelse = deltaker.status.aarsak?.beskrivelse)
                        .getVisningsnavn()
                },
                opprettetDato = deltaker.status.opprettet,
            ),
            registrertDato = innsoktDato.atStartOfDay(),
            dagerPerUke = deltaker.dagerPerUke,
            prosentStilling = deltaker.deltakelsesprosent,
            endretDato = maxOf(deltaker.status.opprettet, deltaker.sistEndret),
            kilde = deltaker.kilde,
            innhold = deltaker.deltakelsesinnhold?.toDeltakelsesinnholdDto(),
            deltakelsesmengder = deltakelsesmengderDto(deltaker, deltakerhistorikk),
        )
    }

    suspend fun buildDeltakerV2Record(deltaker: Deltaker, forcedUpdate: Boolean? = false): DeltakerKafkaPayload {
        val deltakerhistorikk = deltakerHistorikkService.getForDeltaker(deltaker.id)
        val vurderinger = vurderingRepository.getForDeltaker(deltaker.id)
        val sisteEndring = deltakerhistorikk.getSisteEndring()
        val innsoktDato = deltakerhistorikk.getInnsoktDato()
            ?: throw IllegalStateException("Skal ikke produsere deltaker som mangler vedtak til topic")
        val navEnhet = deltaker.navBruker.navEnhetId
            ?.let { navEnhetService.hentEllerOpprettNavEnhet(it) }

        val navAnsatt = deltaker.navBruker.navVeilederId
            ?.let { navAnsattService.hentEllerOpprettNavAnsatt(it) }
        if (deltaker.kilde == Kilde.KOMET &&
            deltakerhistorikk.filterIsInstance<DeltakerHistorikk.Vedtak>().isEmpty() &&
            deltakerhistorikk.filterIsInstance<DeltakerHistorikk.InnsokPaaFellesOppstart>().isEmpty()
        ) {
            throw IllegalStateException(
                "Deltaker med kilde ${Kilde.KOMET} må ha minst et vedtak eller være søkt in for å produseres til topic",
            )
        }

        return DeltakerKafkaPayload(
            id = deltaker.id,
            deltakerlisteId = deltaker.deltakerliste.id,
            deltakerliste = Deltakerliste(
                id = deltaker.deltakerliste.id,
                navn = deltaker.deltakerliste.navn,
                gjennomforingstype = deltaker.deltakerliste.gjennomforingstype,
                tiltak = Tiltak(
                    navn = deltaker.deltakerliste.tiltakstype.navn,
                    tiltakskode = deltaker.deltakerliste.tiltakstype.tiltakskode,
                ),
                startdato = deltaker.deltakerliste.startDato,
                sluttdato = deltaker.deltakerliste.sluttDato,
                oppstartstype = deltaker.deltakerliste.oppstart,
            ),
            personalia = Personalia(
                personId = deltaker.navBruker.personId,
                personident = deltaker.navBruker.personident,
                navn = Navn(
                    fornavn = deltaker.navBruker.fornavn,
                    mellomnavn = deltaker.navBruker.mellomnavn,
                    etternavn = deltaker.navBruker.etternavn,
                ),
                kontaktinformasjon = Kontaktinformasjon(
                    telefonnummer = deltaker.navBruker.telefon,
                    epost = deltaker.navBruker.epost,
                ),
                skjermet = deltaker.navBruker.erSkjermet,
                adresse = deltaker.navBruker.adresse,
                adressebeskyttelse = deltaker.navBruker.adressebeskyttelse,
            ),
            status = DeltakerStatusDto(
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
            innsoktDato = innsoktDato,
            forsteVedtakFattet = deltakerhistorikk.getForsteVedtakFattet(),
            bestillingTekst = deltaker.bakgrunnsinformasjon,
            navKontor = navEnhet?.navn,
            navVeileder = navAnsatt,
            deltarPaKurs = deltaker.deltarPaKurs(),
            kilde = deltaker.kilde,
            innhold = deltaker.deltakelsesinnhold,
            historikk = deltakerhistorikk,
            vurderingerFraArrangor = vurderinger.toDto(),
            sistEndret = deltaker.sistEndret,
            sistEndretAv = sisteEndring?.getSistEndretAv(),
            sistEndretAvEnhet = sisteEndring?.getSistEndretAvEnhet(),
            forcedUpdate = forcedUpdate,
            erManueltDeltMedArrangor = deltaker.erManueltDeltMedArrangor,
            oppfolgingsperioder = deltaker.navBruker.oppfolgingsperioder,
            sisteEndring = sisteEndring?.let {
                SisteEndring(
                    utfortAvNavAnsattId = sisteEndring.getSistEndretAv(),
                    navEnhetId = sisteEndring.getSistEndretAvEnhet(),
                    timestamp = deltaker.sistEndret,
                )
            },
        )
    }

    private fun List<Vurdering>.toDto() = this.map {
        no.nav.amt.lib.models.arrangor.melding.Vurdering(
            id = it.id,
            deltakerId = it.deltakerId,
            opprettetAvArrangorAnsattId = it.opprettetAvArrangorAnsattId,
            opprettet = it.gyldigFra,
            vurderingstype = Vurderingstype.valueOf(it.vurderingstype.name),
            begrunnelse = it.begrunnelse,
        )
    }

    private fun Deltakelsesinnhold.toDeltakelsesinnholdDto() = DeltakerV1Dto.DeltakelsesinnholdDto(
        ledetekst = ledetekst,
        innhold = innhold.filter { it.valgt }.map {
            DeltakerV1Dto.InnholdDto(
                tekst = it.tekst,
                innholdskode = it.innholdskode,
            )
        },
    )

    private fun DeltakerHistorikk.getSistEndretAv(): UUID = when (this) {
        is DeltakerHistorikk.Vedtak -> vedtak.sistEndretAv

        is DeltakerHistorikk.Endring -> endring.endretAv

        is DeltakerHistorikk.EndringFraTiltakskoordinator -> endringFraTiltakskoordinator.endretAv

        is DeltakerHistorikk.InnsokPaaFellesOppstart -> data.innsoktAv

        is DeltakerHistorikk.Forslag,
        is DeltakerHistorikk.EndringFraArrangor,
        is DeltakerHistorikk.ImportertFraArena,
        is DeltakerHistorikk.VurderingFraArrangor,
        -> throw IllegalStateException("Siste endring kan ikke være et forslag eller endring fra arrangør")
    }

    private fun DeltakerHistorikk.getSistEndretAvEnhet(): UUID? = when (this) {
        is DeltakerHistorikk.Vedtak -> vedtak.sistEndretAvEnhet

        is DeltakerHistorikk.Endring -> endring.endretAvEnhet

        is DeltakerHistorikk.InnsokPaaFellesOppstart -> data.innsoktAvEnhet

        is DeltakerHistorikk.EndringFraTiltakskoordinator -> null

        is DeltakerHistorikk.Forslag,
        is DeltakerHistorikk.EndringFraArrangor,
        is DeltakerHistorikk.ImportertFraArena,
        is DeltakerHistorikk.VurderingFraArrangor,
        -> throw IllegalStateException("Siste endring kan ikke være et forslag eller endring fra arrangør")
    }

    private fun List<DeltakerHistorikk>.getForsteVedtakFattet(): LocalDate? {
        getInnsoktDatoFraImportertDeltaker()?.let { return it }

        val vedtak = filterIsInstance<DeltakerHistorikk.Vedtak>().map { it.vedtak }
        val forsteVedtak = vedtak.minByOrNull { it.opprettet }

        return forsteVedtak?.fattet?.toLocalDate()
    }

    private fun List<DeltakerHistorikk>.getSisteEndring() = this.firstOrNull {
        it is DeltakerHistorikk.Vedtak || it is DeltakerHistorikk.Endring
    }

    private fun deltakelsesmengderDto(deltaker: Deltaker, historikk: List<DeltakerHistorikk>): List<DeltakerV1Dto.DeltakelsesmengdeDto> {
        val deltakelsesmengder = if (deltaker.deltakerliste.tiltakstype.harDeltakelsesmengde) {
            val mengder = historikk.toDeltakelsesmengder()
            deltaker.startdato
                ?.let { mengder.periode(deltaker.startdato, deltaker.sluttdato) }
                ?: mengder
        } else {
            emptyList()
        }

        return deltakelsesmengder.map {
            DeltakerV1Dto.DeltakelsesmengdeDto(
                it.deltakelsesprosent,
                it.dagerPerUke,
                it.gyldigFra,
                it.opprettet,
            )
        }
    }
}
