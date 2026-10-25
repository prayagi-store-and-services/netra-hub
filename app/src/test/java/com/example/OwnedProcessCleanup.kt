package com.example

import com.example.data.repository.NetraSafetyRepository
import com.example.nasre.NasreEngine
import kotlinx.coroutines.runBlocking

/** Loaded through the test sandbox, not the runner's host class loader. */
object OwnedProcessCleanup {
    @JvmStatic fun dispose() = runBlocking {
        NasreEngine.disposeIfCreated()
        NetraSafetyRepository.disposeIfCreated()
    }
}
