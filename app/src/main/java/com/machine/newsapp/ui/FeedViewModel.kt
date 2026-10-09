package com.machine.newsapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machine.newsapp.data.FeedKind
import com.machine.newsapp.data.FeedRepository
import com.machine.newsapp.data.NewsFilter
import com.machine.newsapp.data.PublishTokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import com.machine.newsapp.data.CachedFeed

data class DeleteTarget(val kind: FeedKind, val id: Long?, val identity: String, val title: String)
data class TokenSettingsState(val hasToken: Boolean = false, val busy: Boolean = false, val error: String? = null)
sealed interface FeedEvent {
    data class Deleted(val kind: FeedKind) : FeedEvent
    data object TokenSaved : FeedEvent
    data object TokenCleared : FeedEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel(private val repository: FeedRepository, private val tokenStore: PublishTokenStore) : ViewModel() {
    val filter = MutableStateFlow(NewsFilter.ALL)
    val news = filter.flatMapLatest { repository.news(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CachedFeed())
    val deals = repository.deals(includeExpired = true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CachedFeed())
    val picks = repository.picks(includeFuture = true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CachedFeed())
    val statuses = repository.statuses
    val pendingDelete = MutableStateFlow<DeleteTarget?>(null)
    val deleting = MutableStateFlow(false)
    val deleteError = MutableStateFlow<String?>(null)
    val tokenSettings = MutableStateFlow(TokenSettingsState())
    private val eventChannel = Channel<FeedEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        FeedKind.entries.forEach { refresh(it, force = false) }
        viewModelScope.launch {
            try { tokenSettings.value = TokenSettingsState(hasToken = tokenStore.hasToken()) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { tokenSettings.value = TokenSettingsState(error = "Couldn't access secure token storage.") }
        }
    }
    fun selectFilter(value: NewsFilter) {
        if (filter.value == value) return
        filter.value = value
        refresh(FeedKind.NEWS, force = false)
    }
    fun refresh(kind: FeedKind, force: Boolean = true) {
        val selected = filter.value
        viewModelScope.launch { repository.refresh(kind, selected, force) }
    }
    fun key(kind: FeedKind, selected: NewsFilter) = repository.key(kind, selected)

    fun requestDelete(target: DeleteTarget) {
        if (deleting.value) return
        deleteError.value = null
        pendingDelete.value = target
    }
    fun cancelDelete() {
        if (deleting.value) return
        pendingDelete.value = null
        deleteError.value = null
    }
    fun confirmDelete() {
        val target = pendingDelete.value ?: return
        if (deleting.value) return
        deleting.value = true
        deleteError.value = null
        viewModelScope.launch {
            try {
                val token = try { tokenStore.readForDelete() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { throw IllegalArgumentException("Couldn't access the saved token. Open Settings and save it again.") }
                require(!token.isNullOrBlank()) { "Save your publishing token in Settings first." }
                repository.delete(target.kind, target.id, target.identity, token)
                pendingDelete.value = null
                eventChannel.send(FeedEvent.Deleted(target.kind))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { deleteError.value = repository.deleteError(e) }
            finally { deleting.value = false }
        }
    }
    fun saveToken(token: String) {
        if (tokenSettings.value.busy) return
        tokenSettings.value = tokenSettings.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                tokenStore.save(token)
                tokenSettings.value = TokenSettingsState(hasToken = true)
                eventChannel.send(FeedEvent.TokenSaved)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { tokenSettings.value = tokenSettings.value.copy(busy = false, error = "Couldn't save the token securely. Please try again.") }
        }
    }
    fun clearToken() {
        if (tokenSettings.value.busy) return
        tokenSettings.value = tokenSettings.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                tokenStore.clear()
                tokenSettings.value = TokenSettingsState()
                eventChannel.send(FeedEvent.TokenCleared)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { tokenSettings.value = tokenSettings.value.copy(busy = false, error = "Couldn't remove the saved token. Please try again.") }
        }
    }
}
