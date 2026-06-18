package com.example.flowtrack.domain.usecase

import com.example.flowtrack.data.firestore.repositories.TransaccionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservarRevisionLocalUseCase @Inject constructor(
    private val transaccionRepository: TransaccionRepository,
) {
    operator fun invoke(): Flow<Long> {
        return transaccionRepository.observarRevisionLocal()
    }
}
