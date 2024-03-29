package no.nav.amt.deltaker.kafka.utils

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import no.nav.amt.deltaker.Environment
import no.nav.amt.deltaker.application.plugins.objectMapper
import no.nav.amt.deltaker.deltaker.kafka.DeltakerV2Dto
import no.nav.amt.deltaker.utils.AsyncUtils
import java.util.UUID

fun assertProduced(deltakerId: UUID) {
    val cache = mutableMapOf<UUID, DeltakerV2Dto>()

    val consumer = stringStringConsumer(Environment.DELTAKER_V2_TOPIC) { k, v ->
        cache[UUID.fromString(k)] = objectMapper.readValue(v)
    }

    consumer.run()

    AsyncUtils.eventually {
        val cachedDeltaker = cache[deltakerId]!!
        cachedDeltaker.id shouldBe deltakerId
    }

    consumer.stop()
}
