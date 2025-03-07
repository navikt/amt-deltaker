package no.nav.amt.deltaker.arrangormelding.vurdering

class VurderingService(
    val vurderingRepository: VurderingRepository,
) {
    fun upsert(vurdering: Vurdering) {
        vurderingRepository.upsert(vurdering)
    }
}
