package com.personal.mymail.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.mymail.data.Account
import com.personal.mymail.data.MailRepository
import com.personal.mymail.data.MessageRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MailViewModel(app: Application) : AndroidViewModel(app) {
    val repo = MailRepository(app)

    val accounts: StateFlow<List<Account>> =
        repo.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _filter = MutableStateFlow<Long?>(null)
    /** null means "all accounts" (unified inbox). */
    val filter: StateFlow<Long?> = _filter

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<MessageRow>> =
        _filter.flatMapLatest { repo.messages(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    init {
        refresh()
    }

    fun setFilter(accountId: Long?) {
        _filter.value = accountId
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            val errors = repo.refreshAll()
            _refreshing.value = false
            if (errors.isNotEmpty()) {
                _status.value = errors.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            }
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch { repo.deleteAccount(account) }
    }

    fun consumeStatus() {
        _status.value = null
    }
}
