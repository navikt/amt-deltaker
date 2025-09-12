package no.nav.amt.deltaker

data class ShutdownHandlers(
    val shutdownProducers: () -> Unit,
    val shutdownConsumers: suspend () -> Unit,
)
