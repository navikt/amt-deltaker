package no.nav.amt.deltaker.deltakerliste.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.deltakerliste.toModel
import no.nav.amt.deltaker.unleash.UnleashToggle
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

class DeltakerlisteConsumer(
    private val deltakerlisteRepository: DeltakerlisteRepository,
    private val tiltakstypeRepository: TiltakstypeRepository,
    private val arrangorService: ArrangorService,
    private val deltakerService: DeltakerService,
    private val unleashToggle: UnleashToggle,
) : Consumer<UUID, String?> {
    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.DELTAKERLISTE_V2_TOPIC,
        consumeFunc = ::consume,
    )

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    suspend fun consume(key: UUID, value: String?) = if (value == null) {
        deltakerlisteRepository.delete(key)
    } else {
        handterDeltakerliste(objectMapper.readValue(value))
    }

    private suspend fun handterDeltakerliste(deltakerlistePayload: GjennomforingV2KafkaPayload) {
        if (unleashToggle.skipProsesseringAvGjennomforing(deltakerlistePayload.tiltakskode.name)) {
            return
        }

        val arrangor = arrangorService.hentArrangor(deltakerlistePayload.arrangor.organisasjonsnummer)
        val tiltakstype = tiltakstypeRepository.get(deltakerlistePayload.tiltakskode).getOrThrow()

        val oppdatertDeltakerliste = deltakerlistePayload.toModel(
            { gruppe -> gruppe.toModel(arrangor, tiltakstype) },
            { enkeltplass -> enkeltplass.toModel(arrangor, tiltakstype) },
        )

        if (oppdatertDeltakerliste.erAvlystEllerAvbrutt()) {
            deltakerService.avsluttDeltakelserPaaDeltakerliste(oppdatertDeltakerliste)
        }

        deltakerlisteRepository.get(deltakerlistePayload.id).onSuccess { eksisterendeDeltakerliste ->
            if (oppdatertDeltakerliste.sluttDato != null &&
                eksisterendeDeltakerliste.sluttDato != null &&
                oppdatertDeltakerliste.sluttDato < eksisterendeDeltakerliste.sluttDato
            ) {
                deltakerService.avgrensSluttdatoerTil(oppdatertDeltakerliste)
            }
        }

        deltakerlisteRepository.upsert(oppdatertDeltakerliste)
    }
}
