package com.example.nasre

/**
 * Base interface for all NASRE modules
 */
interface INasreModule {
    fun initialize()
    fun start()
    fun stop()
    suspend fun dispose()
    fun getStatus(): String
}
