package dev.epriam.connect

import android.app.Application
import dev.epriam.connect.domain.PriamRepository

class PriamApplication : Application() {
    val repository: PriamRepository by lazy { PriamRepository(this) }
}
