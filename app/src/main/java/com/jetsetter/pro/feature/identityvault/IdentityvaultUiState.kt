package com.jetsetter.pro.feature.identityvault

/**
 * Single immutable UI-state object for the Identity Vault screen.
 *
 * [isUnlocked] gates the whole vault (mock biometric unlock); [isAuthenticating] is the brief
 * "scanning" window between tapping Face ID and the vault opening. [revealedIds] tracks which
 * individual items are currently shown in the clear once unlocked — it is always cleared on lock so
 * nothing leaks. The vault deliberately re-locks on every launch (lock state is never persisted),
 * while the credentials themselves survive restarts via the repository.
 *
 * Summary counts are derived from [entries] so they can never drift out of sync with the list.
 */
data class IdentityvaultUiState(
    val entries: List<IdentityVaultEntry> = emptyList(),
    val isUnlocked: Boolean = false,
    val isAuthenticating: Boolean = false,
    val revealedIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val showAddSheet: Boolean = false,
    val errorMessage: String? = null,
) {
    val itemCount: Int get() = entries.size
    val validCount: Int get() = entries.count { it.status == IdentityVaultStatus.VALID }
    val expiringCount: Int get() = entries.count { it.status == IdentityVaultStatus.EXPIRING_SOON }
    val expiredCount: Int get() = entries.count { it.status == IdentityVaultStatus.EXPIRED }
    val cautionCount: Int get() = entries.count { it.passportCaution }

    /** True once the vault is open and at least one credential is shown in the clear. */
    val allRevealed: Boolean get() = entries.isNotEmpty() && revealedIds.size == entries.size
}
