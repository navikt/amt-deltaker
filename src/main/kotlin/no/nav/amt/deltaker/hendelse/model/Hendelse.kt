package no.nav.amt.deltaker.hendelse.model

import java.time.LocalDateTime

data class Hendelse(
    val opprettet: LocalDateTime,
    val deltaker: HendelseDeltaker,
    val ansvarlig: HendelseAnsvarlig,
    val endring: HendelseEndring,
)
