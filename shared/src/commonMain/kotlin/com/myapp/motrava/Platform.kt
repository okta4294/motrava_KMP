package com.myapp.motrava

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform