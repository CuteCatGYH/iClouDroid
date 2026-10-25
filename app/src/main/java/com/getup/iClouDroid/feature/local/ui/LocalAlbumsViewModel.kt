package com.getup.iClouDroid.feature.local.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.getup.iClouDroid.IClouDroidApp
import com.getup.iClouDroid.feature.local.data.LocalAlbum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocalAlbumsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as IClouDroidApp).container.localAlbumRepository

    private val _albums = MutableStateFlow<List<LocalAlbum>>(emptyList())
    val albums: StateFlow<List<LocalAlbum>> = _albums.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadAlbums() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _albums.value = repository.queryLocalAlbums()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
