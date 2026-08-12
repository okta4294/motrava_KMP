package com.myapp.motrava.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.motrava.domain.model.User
import com.myapp.motrava.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _userState = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val userState: StateFlow<ProfileState> = _userState

    init {
        fetchProfile()
    }

    private fun fetchProfile() {
        viewModelScope.launch {
            _userState.value = ProfileState.Loading
            val result = authRepository.getMe()
            result.onSuccess { user ->
                _userState.value = ProfileState.Success(user)
            }.onFailure { error ->
                _userState.value = ProfileState.Error(error.message ?: "Unknown error")
            }
        }
    }
}

sealed class ProfileState {
    object Loading : ProfileState()
    data class Success(val user: User) : ProfileState()
    data class Error(val message: String) : ProfileState()
}
