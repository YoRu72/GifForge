package com.mediaforge.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mediaforge.app.media.LibraryFilter
import com.mediaforge.app.media.LibraryItem
import com.mediaforge.app.media.MediaLibrary
import kotlinx.coroutines.launch

/** Keeps the scanned library in memory so reopening the finder is instant. */
class MediaViewModel(app: Application) : AndroidViewModel(app) {
    var items by mutableStateOf<List<LibraryItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    private var loaded = false

    // Browser UI state lives here so it survives opening the editor and coming back
    var filter by mutableStateOf(LibraryFilter())
    var gridView by mutableStateOf(false)
    var tab by mutableIntStateOf(0)
    var path by mutableStateOf<String?>(null)
    var searching by mutableStateOf(false)
    var everywhere by mutableStateOf(false)

    fun load(force: Boolean = false) {
        if (loading || (loaded && !force)) return
        loading = true
        viewModelScope.launch {
            items = try {
                MediaLibrary.load(getApplication())
            } catch (e: Exception) {
                emptyList()
            }
            loaded = true
            loading = false
        }
    }
}
