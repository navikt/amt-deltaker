package no.nav.amt.deltaker.deltaker.vurdering

import java.util.UUID

class VurderingService(
    val vurderingRepository: VurderingRepository,
) {
    fun upsert(vurdering: Vurdering) {
        vurderingRepository.upsert(vurdering)
    }

    fun getForDeltaker(deltakerId: UUID): List<Vurdering> {
        return vurderingRepository.getForDeltaker(deltakerId)
    }

    fun deleteForDeltaker(deltakerId: UUID) = vurderingRepository.deleteForDeltaker(deltakerId)
}
