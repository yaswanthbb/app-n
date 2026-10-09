package com.machine.newsapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.machine.newsapp.data.FeedKind
import com.machine.newsapp.data.FeedRepository
import com.machine.newsapp.data.NewsFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.machine.newsapp.data.CachedFeed

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel(private val repository: FeedRepository) : ViewModel() {
    val filter = MutableStateFlow(NewsFilter.ALL)
    val news = filter.flatMapLatest { repository.news(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CachedFeed())
    val deals = repository.deals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CachedFeed())
    val picks = repository.picks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CachedFeed())
    val statuses = repository.statuses

    init {
        FeedKind.entries.forEach { refresh(it, force = false) }
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
}
