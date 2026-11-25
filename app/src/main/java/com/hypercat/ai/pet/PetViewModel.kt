package com.hypercat.ai.pet

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PetUiState(
    val selectedPet: PetProfile = PetCatalog.all.first(),
    val mood: PetMood = PetMood.IDLE,
    val status: String = "我在这里，随时听候吩咐。"
)

class PetViewModel : ViewModel() {
    private val _state = MutableStateFlow(PetUiState())
    val state: StateFlow<PetUiState> = _state.asStateFlow()

    fun selectPet(pet: PetProfile) {
        _state.value = _state.value.copy(selectedPet = pet, mood = PetMood.HAPPY, status = "${pet.name} 已加入！")
    }

    fun setMood(mood: PetMood, message: String) {
        _state.value = _state.value.copy(mood = mood, status = message)
    }
}
