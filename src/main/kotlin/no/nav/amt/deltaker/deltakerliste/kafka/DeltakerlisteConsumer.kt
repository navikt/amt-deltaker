package no.nav.amt.deltaker.deltakerliste.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltakerliste.DeltakerlisteRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

class DeltakerlisteConsumer(
    private val repository: DeltakerlisteRepository,
    private val tiltakstypeRepository: TiltakstypeRepository,
    private val arrangorService: ArrangorService,
    private val deltakerService: DeltakerService,
    private val topic: String,
) : Consumer<UUID, String?> {
    private val consumer = buildManagedKafkaConsumer(
        topic = topic,
        consumeFunc = ::consume,
    )

    override fun start() = consumer.start()

    override suspend fun close() = consumer.close()

    override suspend fun consume(key: UUID, value: String?) {
        if (value == null) {
            repository.delete(key)
        } else {
            handterDeltakerliste(objectMapper.readValue(value))
        }
    }

    @Suppress("DuplicatedCode")
    private suspend fun handterDeltakerliste(deltakerlisteDto: DeltakerlisteDto) {
        if (!deltakerlisteDto.tiltakstype.erStottet()) return

        val tiltakskode = Tiltakskode.valueOf(deltakerlisteDto.tiltakstype.tiltakskode)

        val oppdatertDeltakerliste = deltakerlisteDto.toModel(
            arrangor = hentArrangor(deltakerlisteDto),
            tiltakstype = tiltakstypeRepository.get(tiltakskode).getOrThrow(),
        )

        if (oppdatertDeltakerliste.erAvlystEllerAvbrutt()) {
            deltakerService.avsluttDeltakelserPaaDeltakerliste(oppdatertDeltakerliste)
        }

        repository.get(deltakerlisteDto.id).onSuccess { eksisterendeDeltakerliste ->
            if (oppdatertDeltakerliste.sluttDato != null &&
                eksisterendeDeltakerliste.sluttDato != null &&
                oppdatertDeltakerliste.sluttDato < eksisterendeDeltakerliste.sluttDato
            ) {
                deltakerService.avgrensSluttdatoerTil(oppdatertDeltakerliste)
            }
        }

        repository.upsert(oppdatertDeltakerliste)
    }

    private suspend fun hentArrangor(deltakerlisteDto: DeltakerlisteDto): Arrangor = arrangorService.hentArrangor(
        when (topic) {
            Environment.DELTAKERLISTE_V1_TOPIC -> deltakerlisteDto.virksomhetsnummer
            else -> deltakerlisteDto.arrangor?.organisasjonsnummer
        } ?: throw IllegalStateException("Virksomhetsnummer mangler"),
    )
}
