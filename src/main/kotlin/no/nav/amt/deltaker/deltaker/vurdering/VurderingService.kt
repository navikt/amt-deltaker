package no.nav.amt.deltaker.deltaker.vurdering

class VurderingService(
    val vurderingRepository: VurderingRepository,
) {
    fun upsert(vurdering: Vurdering) {
        vurderingRepository.upsert(vurdering)
    }
}
