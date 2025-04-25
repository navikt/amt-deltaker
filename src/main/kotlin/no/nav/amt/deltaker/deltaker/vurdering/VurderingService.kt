package no.nav.amt.deltaker.deltaker.vurdering

import java.util.UUID

class VurderingService(
    val vurderingRepository: VurderingRepository,
) {
    fun upsert(vurdering: Vurdering) {
        vurderingRepository.upsert(vurdering)
    }

    fun deleteForDeltaker(deltakerId: UUID) = vurderingRepository.deleteForDeltaker(deltakerId)
}
