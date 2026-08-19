package com.myapp.motrava.di

import com.myapp.motrava.data.local.TokenManager
import com.myapp.motrava.data.remote.ApiService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val networkModule = module {
    single {
        val tokenManager = get<TokenManager>()

        HttpClient(createHttpClientEngine()) {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 20_000
            }

            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                    explicitNulls = false
                })
            }

            install(WebSockets) {
                pingInterval = 20_000
            }

            defaultRequest {
                url("https://be-motrava.taufikdev.net/")
                val token = tokenManager.accessToken
                
                val path = url.encodedPath
                val isAuthRefresh = path.contains("auth/refresh")
                
                if (token != null && !isAuthRefresh) {
                    header("Authorization", "Bearer $token")
                }
            }

            // Expect non-success but don't throw — let safeRequest handle it
            expectSuccess = false
        }
    }

    single { ApiService(get(), get()) }
}
