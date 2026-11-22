package com.duduezn.budgettracker

import android.app.Application
import com.duduezn.budgettracker.data.work.BudgetAlertScheduler
import com.duduezn.budgettracker.di.AppContainer
import com.duduezn.budgettracker.di.DefaultAppContainer
import com.duduezn.budgettracker.util.BudgetAlertNotifier
import com.duduezn.budgettracker.util.NotificationHelper

class BudgetTrackerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        container.initialize()
        NotificationHelper.createChannels(this)
        BudgetAlertNotifier.createChannels(this)
        BudgetAlertScheduler.schedule(this)
    }
}
