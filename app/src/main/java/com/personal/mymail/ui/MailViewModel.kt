package com.personal.mymail.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.mymail.data.Account
import com.personal.mymail.data.MailFolder
import com.personal.mymail.data.MailRepository
import com.personal.mymail.data.MessageRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MailViewModel(app: Application) : AndroidViewModel(app) {
    val repo = MailRepository(app)

    val accounts: StateFlow<List<Account>> =
        repo.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _filter = MutableStateFlow<Long?>(null)
    /** null means "all accounts" (combined inbox). */
    val filter: StateFlow<Long?> = _filter

    private val _folder = MutableStateFlow("INBOX")
    /** Full name of the folder being viewed (only used when one account is selected). */
    val folder: StateFlow<String> = _folder

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<MessageRow>> =
        combine(_filter, _folder) { account, folderName -> account to folderName }
            .flatMapLatest { (account, folderName) ->
                if (account == null) repo.inboxes() else repo.folderMessages(account, folderName)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Folders the user has chosen to sync for the selected account. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val folders: StateFlow<List<MailFolder>> =
        _filter.flatMapLatest { account ->
            if (account == null) flowOf(emptyList()) else repo.enabledFolders(account)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    init {
        refresh()
    }

    fun setFilter(accountId: Long?) {
        _filter.value = accountId
        _folder.value = "INBOX"
    }

    fun setFolder(fullName: String) {
        _folder.value = fullName
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            val result = repo.refreshAll()
            _refreshing.value = false
            if (result.errors.isNotEmpty()) {
                _status.value = result.errors.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            }
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch { repo.deleteAccount(account) }
    }

    fun setFolderEnabled(folder: MailFolder, enabled: Boolean) {
        viewModelScope.launch { repo.setFolderEnabled(folder.id, enabled) }
    }

    fun consumeStatus() {
        _status.value = null
    }
}
