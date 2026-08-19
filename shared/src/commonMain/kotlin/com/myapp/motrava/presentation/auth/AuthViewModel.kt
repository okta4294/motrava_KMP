package com.myapp.motrava.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.motrava.domain.model.AuthResult
import com.myapp.motrava.domain.model.User
import com.myapp.motrava.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
// import com.google.firebase.messaging.FirebaseMessaging

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private fun executeAuthCall(call: suspend () -> AuthResult<User>) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = call()
            if (result is AuthResult.Success) {
                registerDeviceToken()
            }
            _authState.value = when (result) {
                is AuthResult.Success -> AuthState.Success(result.data)
                is AuthResult.Error -> AuthState.Error(result.message)
            }
        }
    }

    private fun registerDeviceToken() {
        // FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        //     if (task.isSuccessful) {
        //         val token = task.result
        //         viewModelScope.launch {
        //             authRepository.registerDevice(token)
        //         }
        //     }
        // }
        println("Device token registration skipped in commonMain")
    }

    fun googleLogin(idToken: String) = executeAuthCall {
        authRepository.googleLogin(idToken)
    }

    fun loginWithEmail(email: String, pass: String) = executeAuthCall {
        authRepository.loginWithEmail(email, pass)
    }

    fun registerWithEmail(name: String, email: String, pass: String) = executeAuthCall {
        authRepository.registerWithEmail(name, email, pass)
    }

    fun logout() {
        authRepository.logout()
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    fun setError(message: String) {
        _authState.value = AuthState.Error(message)
    }

    fun setLoading() {
        _authState.value = AuthState.Loading
    }
}
