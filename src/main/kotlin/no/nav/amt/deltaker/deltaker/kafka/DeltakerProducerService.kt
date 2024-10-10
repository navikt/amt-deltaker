package no.nav.amt.deltaker.deltaker.kafka

import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.deltaker.model.getVisningsnavn
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import java.time.LocalDateTime
import java.util.UUID

class DeltakerProducerService(
    private val deltakerV2MapperService: DeltakerV2MapperService,
    private val deltakerProducer: DeltakerProducer,
    private val deltakerV1Producer: DeltakerV1Producer,
    private val unleashToggle: UnleashToggle,
) {
    suspend fun produce(
        deltaker: Deltaker,
        forcedUpdate: Boolean? = false,
        publiserTilDeltakerV1: Boolean = true,
    ) {
        if (deltaker.status.type == DeltakerStatus.Type.KLADD) return
        val deltakerV2Dto = deltakerV2MapperService.tilDeltakerV2Dto(deltaker, forcedUpdate)

        deltakerProducer.produce(deltakerV2Dto)
        if (publiserTilDeltakerV1 && unleashToggle.erKometMasterForTiltakstype(deltaker.deltakerliste.tiltakstype.arenaKode)) {
            deltakerV1Producer.produce(toDeltakerV1Dto(deltakerV2Dto))
        }
    }

    fun tombstone(deltakerId: UUID) {
        deltakerProducer.produceTombstone(deltakerId)
        deltakerV1Producer.produceTombstone(deltakerId)
    }

    private fun toDeltakerV1Dto(deltakerV2Dto: DeltakerV2Dto): DeltakerV1Dto {
        return DeltakerV1Dto(
            id = deltakerV2Dto.id,
            gjennomforingId = deltakerV2Dto.deltakerlisteId,
            personIdent = deltakerV2Dto.personalia.personident,
            startDato = deltakerV2Dto.oppstartsdato,
            sluttDato = deltakerV2Dto.sluttdato,
            status = DeltakerV1Dto.DeltakerStatusDto(
                type = deltakerV2Dto.status.type,
                aarsak = deltakerV2Dto.status.aarsak,
                aarsakTekst = deltakerV2Dto.status.aarsak?.let {
                    DeltakerStatus.Aarsak(type = it, beskrivelse = deltakerV2Dto.status.aarsaksbeskrivelse).getVisningsnavn()
                },
                opprettetDato = deltakerV2Dto.status.opprettetDato,
            ),
            registrertDato = deltakerV2Dto.innsoktDato.atStartOfDay(),
            dagerPerUke = deltakerV2Dto.dagerPerUke,
            prosentStilling = deltakerV2Dto.prosentStilling?.toFloat(),
            endretDato = deltakerV2Dto.sistEndret ?: LocalDateTime.now(),
            kilde = deltakerV2Dto.kilde,
        )
    }
}
