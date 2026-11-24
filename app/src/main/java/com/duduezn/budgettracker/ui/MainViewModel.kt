package com.duduezn.budgettracker.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Étapes de démarrage : onboarding, définition du budget, création du PIN, déverrouillage, application. */
enum class GateStage {
    ONBOARDING,
    BUDGET_SETUP,
    PIN_SETUP,
    UNLOCK,
    ROOT
}

/**
 * Conserve l'étape courante pendant les rotations d'écran.
 * Un ViewModel ne survit pas à la mort du processus : un nouveau démarrage repasse
 * donc obligatoirement par l'écran de déverrouillage.
 */
class MainViewModel : ViewModel() {

    private val _stage = MutableStateFlow<GateStage?>(null)
    val stage: StateFlow<GateStage?> = _stage.asStateFlow()

    fun initialize(initial: GateStage) {
        if (_stage.value == null) {
            _stage.value = initial
        }
    }

    fun setStage(stage: GateStage) {
        _stage.value = stage
    }
}
