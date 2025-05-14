package no.nav.amt.deltaker.deltaker.vurdering

import java.util.UUID

class VurderingService(
    private val vurderingRepository: VurderingRepository,
) {
    fun upsert(vurdering: Vurdering) {
        vurderingRepository.upsert(vurdering)
    }

    fun getForDeltaker(deltakerId: UUID): List<Vurdering> = vurderingRepository.getForDeltaker(deltakerId)

    fun getSisteForDeltaker(deltakerId: UUID) = getForDeltaker(deltakerId).maxByOrNull { it.gyldigFra }

    fun deleteForDeltaker(deltakerId: UUID) = vurderingRepository.deleteForDeltaker(deltakerId)
}
