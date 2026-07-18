package com.jetsetter.pro.feature.lovedones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.data.lovedones.LovedOne
import com.jetsetter.pro.core.data.lovedones.LovedOnesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Loved Ones management screen (spec §3.3, plan B8): the list of contacts to notify on
 * takeoff / landing, with add/edit/delete plus per-contact notification toggles. Persistence is
 * the shared [LovedOnesRepository] (`jetsetter_loved_ones`), which IRIS's
 * `flightActions(notifyLovedOnes)` tool also reads.
 */
@HiltViewModel
class LovedonesViewModel @Inject constructor(
    private val repository: LovedOnesRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(LovedonesUiState(isLoading = true))
    val ui: StateFlow<LovedonesUiState> = _ui.asStateFlow()

    init {
        // Fold the repository Flow into UI state — the canonical collect pattern used across
        // the app. No seed: the list starts empty until the user adds someone.
        viewModelScope.launch {
            repository.observe().collect { contacts ->
                _ui.update { it.copy(contacts = contacts, isLoading = false) }
            }
        }
    }

    /** Opens the sheet in add mode (blank draft). */
    fun showAddSheet() {
        _ui.update { it.copy(showEditSheet = true, editing = null) }
    }

    /** Opens the sheet in edit mode prefilled with [contact]. */
    fun showEditSheet(contact: LovedOne) {
        _ui.update { it.copy(showEditSheet = true, editing = contact) }
    }

    fun dismissSheet() {
        _ui.update { it.copy(showEditSheet = false, editing = null) }
    }

    /**
     * Persists the sheet's draft: updates the contact being edited in place (same id), or appends
     * a new one. Blank name or phone number is rejected (the sheet also disables the button).
     * Flips [LovedonesUiState.isSaving] for the brief persist, then closes the sheet.
     */
    fun saveContact(
        name: String,
        phoneNumber: String,
        notifyOnTakeoff: Boolean,
        notifyOnLanding: Boolean,
    ) {
        val trimmedName = name.trim()
        val trimmedPhone = phoneNumber.trim()
        if (trimmedName.isEmpty() || trimmedPhone.isEmpty()) return

        val editing = _ui.value.editing
        val contact = editing?.copy(
            name = trimmedName,
            phoneNumber = trimmedPhone,
            notifyOnTakeoff = notifyOnTakeoff,
            notifyOnLanding = notifyOnLanding,
        ) ?: LovedOne(
            name = trimmedName,
            phoneNumber = trimmedPhone,
            notifyOnTakeoff = notifyOnTakeoff,
            notifyOnLanding = notifyOnLanding,
        )

        _ui.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            repository.upsert(contact)
            _ui.update { it.copy(isSaving = false, showEditSheet = false, editing = null) }
        }
    }

    fun deleteContact(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    /** Per-contact takeoff-notification toggle, flipped directly from the list row. */
    fun setNotifyOnTakeoff(id: String, value: Boolean) {
        val contact = _ui.value.contacts.firstOrNull { it.id == id } ?: return
        viewModelScope.launch { repository.upsert(contact.copy(notifyOnTakeoff = value)) }
    }

    /** Per-contact landing-notification toggle, flipped directly from the list row. */
    fun setNotifyOnLanding(id: String, value: Boolean) {
        val contact = _ui.value.contacts.firstOrNull { it.id == id } ?: return
        viewModelScope.launch { repository.upsert(contact.copy(notifyOnLanding = value)) }
    }
}
