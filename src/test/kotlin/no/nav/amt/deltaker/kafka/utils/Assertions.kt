package no.nav.amt.deltaker.kafka.utils

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerV1Dto
import no.nav.amt.deltaker.deltaker.kafka.dto.DeltakerV2Dto
import no.nav.amt.lib.kafka.utils.stringStringConsumer
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.hendelse.Hendelse
import no.nav.amt.lib.models.hendelse.HendelseType
import no.nav.amt.lib.testing.AsyncUtils
import no.nav.amt.lib.testing.shouldBeCloseTo
import java.util.UUID
import kotlin.reflect.KClass

fun assertProduced(deltakerId: UUID) {
    val cache = mutableMapOf<UUID, DeltakerV2Dto>()

    val consumer = stringStringConsumer(Environment.DELTAKER_V2_TOPIC) { k, v ->
        cache[UUID.fromString(k)] = objectMapper.readValue(v)
    }

    consumer.start()

    AsyncUtils.eventually {
        val cachedDeltaker = cache[deltakerId]!!
        cachedDeltaker.id shouldBe deltakerId
    }

    consumer.stop()
}

fun assertProducedDeltakerV1(deltakerId: UUID) {
    val cache = mutableMapOf<UUID, DeltakerV1Dto>()

    val consumer = stringStringConsumer(Environment.DELTAKER_V1_TOPIC) { k, v ->
        cache[UUID.fromString(k)] = objectMapper.readValue(v)
    }

    consumer.start()

    AsyncUtils.eventually {
        val cachedDeltaker = cache[deltakerId]!!
        cachedDeltaker.id shouldBe deltakerId
    }

    consumer.stop()
}

fun assertOnProducedDeltaker(deltaker: DeltakerV2Dto) {
    val cache = mutableMapOf<UUID, DeltakerV2Dto>()

    val consumer = stringStringConsumer(Environment.DELTAKER_V2_TOPIC) { k, v ->
        cache[UUID.fromString(k)] = objectMapper.readValue(v)
    }

    consumer.start()

    AsyncUtils.eventually {
        val cachedDeltaker = cache[deltaker.id]!!
        cachedDeltaker.id shouldBe deltaker.id
        cachedDeltaker.deltakerlisteId shouldBe deltaker.deltakerlisteId
        cachedDeltaker.personalia shouldBe deltaker.personalia
        cachedDeltaker.status shouldBe deltaker.status
        cachedDeltaker.dagerPerUke shouldBe deltaker.dagerPerUke
        cachedDeltaker.prosentStilling shouldBe deltaker.prosentStilling
        cachedDeltaker.oppstartsdato shouldBe deltaker.oppstartsdato
        cachedDeltaker.sluttdato shouldBe deltaker.sluttdato
        cachedDeltaker.innsoktDato shouldBe deltaker.innsoktDato
        cachedDeltaker.forsteVedtakFattet shouldBe deltaker.forsteVedtakFattet
        cachedDeltaker.bestillingTekst shouldBe deltaker.bestillingTekst
        cachedDeltaker.navKontor shouldBe deltaker.navKontor
        cachedDeltaker.navVeileder shouldBe deltaker.navVeileder
        cachedDeltaker.deltarPaKurs shouldBe deltaker.deltarPaKurs
        cachedDeltaker.kilde shouldBe deltaker.kilde
        cachedDeltaker.innhold shouldBe deltaker.innhold
        cachedDeltaker.historikk!!.assertComparable(deltaker.historikk!!)

        cachedDeltaker.sistEndretAv shouldBe deltaker.sistEndretAv
        cachedDeltaker.sistEndretAvEnhet shouldBe deltaker.sistEndretAvEnhet
        cachedDeltaker.sistEndret shouldBe deltaker.sistEndret
    }

    consumer.stop()
}

fun List<DeltakerHistorikk>.assertComparable(deltakerHistorikk: List<DeltakerHistorikk>) {
    this.forEach { historikkElement ->
        run {
            when (historikkElement) {
                is DeltakerHistorikk.ImportertFraArena -> {
                    val compareTo =
                        deltakerHistorikk.find { it is DeltakerHistorikk.ImportertFraArena } as DeltakerHistorikk.ImportertFraArena
                    historikkElement.importertFraArena.deltakerVedImport shouldBe compareTo.importertFraArena.deltakerVedImport
                }

                else -> throw NotImplementedError()
            }
        }
    }
}

fun assertProducedFeilregistrert(deltakerId: UUID) {
    val cache = mutableMapOf<UUID, DeltakerV2Dto>()

    val consumer = stringStringConsumer(Environment.DELTAKER_V2_TOPIC) { k, v ->
        cache[UUID.fromString(k)] = objectMapper.readValue(v)
    }

    consumer.start()

    AsyncUtils.eventually {
        val cachedDeltaker = cache[deltakerId]!!
        cachedDeltaker.id shouldBe deltakerId
        cachedDeltaker.status.type shouldBe DeltakerStatus.Type.FEILREGISTRERT
        cachedDeltaker.dagerPerUke shouldBe null
        cachedDeltaker.prosentStilling shouldBe null
        cachedDeltaker.oppstartsdato shouldBe null
        cachedDeltaker.sluttdato shouldBe null
        cachedDeltaker.bestillingTekst shouldBe null
        cachedDeltaker.innhold shouldBe null
        cachedDeltaker.historikk?.filterIsInstance<DeltakerHistorikk.Endring>() shouldBe emptyList()
    }

    consumer.stop()
}

fun <T : HendelseType> assertProducedHendelse(deltakerId: UUID, hendelsetype: KClass<T>) {
    val cache = mutableMapOf<UUID, Hendelse>()

    val consumer = stringStringConsumer(Environment.DELTAKER_HENDELSE_TOPIC) { k, v ->
        cache[UUID.fromString(k)] = objectMapper.readValue(v)
    }

    consumer.start()

    AsyncUtils.eventually {
        val cachedHendelse = cache[deltakerId]!!
        cachedHendelse.deltaker.id shouldBe deltakerId
        cachedHendelse.payload::class shouldBe hendelsetype
    }

    consumer.stop()
}

fun assertProducedForslag(forslag: Forslag) {
    val cache = mutableMapOf<UUID, Forslag>()

    val consumer = stringStringConsumer(Environment.ARRANGOR_MELDING_TOPIC) { k, v ->
        cache[UUID.fromString(k)] = objectMapper.readValue(v)
    }

    consumer.start()

    AsyncUtils.eventually {
        val cachedForslag = cache[forslag.id]!!
        cachedForslag.id shouldBe forslag.id
        cachedForslag.deltakerId shouldBe forslag.deltakerId
        cachedForslag.endring shouldBe forslag.endring
        cachedForslag.begrunnelse shouldBe forslag.begrunnelse
        cachedForslag.opprettet shouldBeCloseTo forslag.opprettet
        cachedForslag.opprettetAvArrangorAnsattId shouldBe forslag.opprettetAvArrangorAnsattId
        sammenlignForslagStatus(cachedForslag.status, forslag.status)
    }

    consumer.stop()
}

fun sammenlignForslagStatus(a: Forslag.Status, b: Forslag.Status) {
    when (a) {
        is Forslag.Status.VenterPaSvar -> {
            b as Forslag.Status.VenterPaSvar
            a shouldBe b
        }

        is Forslag.Status.Avvist -> {
            b as Forslag.Status.Avvist
            a.avvist shouldBeCloseTo b.avvist
            a.avvistAv shouldBe b.avvistAv
            a.begrunnelseFraNav shouldBe b.begrunnelseFraNav
        }

        is Forslag.Status.Godkjent -> {
            b as Forslag.Status.Godkjent
            a.godkjent shouldBeCloseTo b.godkjent
            a.godkjentAv shouldBe b.godkjentAv
        }

        is Forslag.Status.Tilbakekalt -> {
            b as Forslag.Status.Tilbakekalt
            a.tilbakekalt shouldBeCloseTo b.tilbakekalt
            a.tilbakekaltAvArrangorAnsattId shouldBe b.tilbakekaltAvArrangorAnsattId
        }

        is Forslag.Status.Erstattet -> {
            b as Forslag.Status.Erstattet
            a.erstattetMedForslagId shouldBe b.erstattetMedForslagId
            a.erstattet shouldBeCloseTo b.erstattet
        }
    }
}
