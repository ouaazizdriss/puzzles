package com.driss.puzzles

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.driss.puzzles.data.Prefs
import com.driss.puzzles.data.PuzzleRepository
import com.driss.puzzles.data.SaveStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PuzzlesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency container; the app is small enough not to need a DI framework. */
class AppContainer(val context: Context) {
    /** Outlives screens, so saves started while leaving a game still complete. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository = PuzzleRepository(context)
    val saves = SaveStore(context)
    val prefs = Prefs(context)
}

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as PuzzlesApp).container
