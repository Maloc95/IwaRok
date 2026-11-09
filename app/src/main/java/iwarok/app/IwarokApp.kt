package iwarok.app

import android.app.Application
import kotlinx.coroutines.launch

class IwarokApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.appScope.launch { graph.repository.init() }
    }
}
