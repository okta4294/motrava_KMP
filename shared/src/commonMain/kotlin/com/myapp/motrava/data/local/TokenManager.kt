package com.myapp.motrava.data.local

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TokenManager(
    private val settings: Settings
) {
    private val _loggedOutEvent = MutableSharedFlow<Unit>()
    val loggedOutEvent = _loggedOutEvent.asSharedFlow()

    companion object {
        private const val ACCESS_TOKEN_KEY = "access_token"
        private const val REFRESH_TOKEN_KEY = "refresh_token"
    }

    val accessToken: String?
        get() = settings.getStringOrNull(ACCESS_TOKEN_KEY)

    val refreshToken: String?
        get() = settings.getStringOrNull(REFRESH_TOKEN_KEY)

    fun saveTokens(access: String, refresh: String?) {
        settings[ACCESS_TOKEN_KEY] = access
        if (refresh != null) {
            settings[REFRESH_TOKEN_KEY] = refresh
        }
    }

    var lastSelectedVehicleId: String?
        get() = settings.getStringOrNull("last_selected_vehicle")
        set(value) {
            if (value == null) settings.remove("last_selected_vehicle")
            else settings["last_selected_vehicle"] = value
        }

    var userName: String?
        get() = settings.getStringOrNull("user_name")
        set(value) {
            if (value == null) settings.remove("user_name")
            else settings["user_name"] = value
        }

    fun clearTokens() {
        settings.remove(ACCESS_TOKEN_KEY)
        settings.remove(REFRESH_TOKEN_KEY)
        settings.remove("last_selected_vehicle")
        settings.remove("user_name")
        
        CoroutineScope(Dispatchers.Main).launch {
            _loggedOutEvent.emit(Unit)
        }
    }
}
