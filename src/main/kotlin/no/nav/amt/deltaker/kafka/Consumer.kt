package no.nav.amt.deltaker.kafka

import kotlinx.coroutines.Job

interface Consumer<K, V> {
    suspend fun consume(key: K, value: V)
    fun run(): Job
}
