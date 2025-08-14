package no.nav.amt.deltaker.deltakerliste.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.arrangor.ArrangorService
import no.nav.amt.deltaker.deltaker.DeltakerService
import no.nav.amt.deltaker.deltakerliste.DeltakerListeRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.TiltakstypeRepository
import no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka.arenaKodeTilTiltakstype
import no.nav.amt.deltaker.deltakerliste.tiltakstype.kafka.erStottet
import no.nav.amt.deltaker.utils.buildManagedKafkaConsumer
import no.nav.amt.lib.kafka.Consumer
import no.nav.amt.lib.utils.objectMapper
import java.util.UUID

class DeltakerlisteConsumer(
    private val repository: DeltakerListeRepository,
    private val tiltakstypeRepository: TiltakstypeRepository,
    private val arrangorService: ArrangorService,
    private val deltakerService: DeltakerService,
) : Consumer<UUID, String?> {
    private val consumer = buildManagedKafkaConsumer(
        topic = Environment.DELTAKERLISTE_TOPIC,
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

    private suspend fun handterDeltakerliste(deltakerlisteDto: DeltakerlisteDto) {
        if (!erStottet(deltakerlisteDto.tiltakstype.arenaKode)) return

        val tiltakstype = tiltakstypeRepository.get(arenaKodeTilTiltakstype(deltakerlisteDto.tiltakstype.arenaKode)).getOrThrow()

        val arrangor = arrangorService.hentArrangor(deltakerlisteDto.virksomhetsnummer)

        val oppdatertDeltakerliste = deltakerlisteDto.toModel(arrangor, tiltakstype)
        val gammelDeltakerliste = repository.get(deltakerlisteDto.id)

        if (oppdatertDeltakerliste.erAvlystEllerAvbrutt()) {
            deltakerService.avsluttDeltakelserPaaDeltakerliste(oppdatertDeltakerliste)
        }

        gammelDeltakerliste.onSuccess {
            if (oppdatertDeltakerliste.sluttDato != null &&
                it.sluttDato != null &&
                oppdatertDeltakerliste.sluttDato < it.sluttDato
            ) {
                deltakerService.avgrensSluttdatoerTil(oppdatertDeltakerliste)
            }
        }

        repository.upsert(oppdatertDeltakerliste)
    }
}
