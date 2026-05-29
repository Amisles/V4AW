package org.amisles.v4aw.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.amisles.v4aw.model.HistoryItem
import org.amisles.v4aw.model.VideoInfo
import org.amisles.v4aw.domain.usecase.HistoryUseCase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class HistoryGroup(
    val label: String,
    val items: List<HistoryItem>
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyUseCase: HistoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            historyUseCase.getHistory().collect { history ->
                _uiState.value = _uiState.value.copy(historyItems = history)
            }
        }
    }

    fun getVideoInfo(item: HistoryItem): VideoInfo {
        return VideoInfo(
            url = item.url,
            title = item.title,
            thumbnailUrl = item.thumbnailUrl
        )
    }

    fun toggleSelection(item: HistoryItem) {
        val current = _uiState.value.selectedItems
        _uiState.value = _uiState.value.copy(
            selectedItems = if (current.any { it.id == item.id }) {
                current.filter { it.id != item.id }.toSet()
            } else {
                current + item
            }
        )
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(
            selectedItems = _uiState.value.historyItems.toSet()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedItems = emptySet())
    }

    fun deleteItem(item: HistoryItem) {
        viewModelScope.launch {
            historyUseCase.deleteHistory(item)
        }
    }

    fun deleteSelectedItems() {
        viewModelScope.launch {
            _uiState.value.selectedItems.forEach { item ->
                historyUseCase.deleteHistory(item)
            }
            clearSelection()
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyUseCase.clearHistory()
            clearSelection()
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun getFilteredItems(): List<HistoryItem> {
        val query = _uiState.value.searchQuery.trim()
        return if (query.isEmpty()) {
            _uiState.value.historyItems
        } else {
            _uiState.value.historyItems.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.url.contains(query, ignoreCase = true)
            }
        }
    }

    fun getGroupedHistory(): List<HistoryGroup> {
        val items = getFilteredItems()
        val today = getStartOfDay(0)
        val yesterday = getStartOfDay(1)

        val groups = mutableMapOf<String, MutableList<HistoryItem>>()

        for (item in items) {
            val itemDate = item.timestamp
            val label = when {
                itemDate >= today -> "today"
                itemDate >= yesterday -> "yesterday"
                else -> {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    sdf.format(Date(itemDate))
                }
            }
            groups.getOrPut(label) { mutableListOf() }.add(item)
        }

        return groups.map { (label, items) -> HistoryGroup(label, items) }
    }

    private fun getStartOfDay(daysAgo: Int): Long {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}

data class HistoryUiState(
    val historyItems: List<HistoryItem> = emptyList(),
    val selectedItems: Set<HistoryItem> = emptySet(),
    val searchQuery: String = ""
)
