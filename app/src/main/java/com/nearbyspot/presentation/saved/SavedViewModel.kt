package com.nearbyspot.presentation.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearbyspot.domain.model.Place
import com.nearbyspot.domain.usecase.GetFavoritesUseCase
import com.nearbyspot.domain.usecase.ToggleFavoriteUseCase
import com.nearbyspot.domain.repository.PlaceRepository
import com.nearbyspot.util.PlacePhotoProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SavedUiState(
    val isLoading: Boolean = true,
    val favorites: List<Place> = emptyList(),
    val selectedPlace: Place? = null,
)

@HiltViewModel
class SavedViewModel @Inject constructor(
    private val getFavoritesUseCase: GetFavoritesUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val repository: PlaceRepository,
    val photoProvider: PlacePhotoProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedUiState())
    val uiState: StateFlow<SavedUiState> = _uiState.asStateFlow()

    init {
        loadFavorites()
        viewModelScope.launch {
            repository.observeFavoriteIds()
                .distinctUntilChanged()
                .drop(1)
                .collect { loadFavorites() }
        }
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val favorites = getFavoritesUseCase()
            _uiState.update { it.copy(favorites = favorites, isLoading = false) }
        }
    }

    fun onPlaceSelected(place: Place?) {
        _uiState.update { it.copy(selectedPlace = place) }
    }

    suspend fun fetchReviews(placeId: String): List<com.nearbyspot.domain.model.Review> {
        return repository.fetchReviews(placeId)
    }

    fun onRemoveFavorite(place: Place) {
        viewModelScope.launch {
            toggleFavoriteUseCase(place)
        }
    }
}
