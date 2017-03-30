package com.okkero.skedule

import kotlinx.coroutines.experimental.CancellableContinuation
import kotlinx.coroutines.experimental.CoroutineDispatcher
import kotlinx.coroutines.experimental.Delay
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.TimeUnit
import kotlin.coroutines.experimental.CoroutineContext

private val bukkitScheduler
    get() = Bukkit.getScheduler()

private fun TimeUnit.toBukkitTicks(time: Long): Long {
    return toMillis(time) / 50
}

class BukkitDispatcher(val plugin: JavaPlugin, async: Boolean = false) : CoroutineDispatcher(), Delay {

    private val runTaskLater: (Plugin, Runnable, Long) -> BukkitTask =
            if (async)
                bukkitScheduler::runTaskLater
            else
                bukkitScheduler::runTaskLaterAsynchronously
    private val runTask: (Plugin, Runnable) -> BukkitTask =
            if (async)
                bukkitScheduler::runTask
            else
                bukkitScheduler::runTaskAsynchronously

    override fun scheduleResumeAfterDelay(time: Long, unit: TimeUnit, continuation: CancellableContinuation<Unit>) {
        runTaskLater(plugin, Runnable { continuation.resume(Unit) }, unit.toBukkitTicks(time))
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (Bukkit.isPrimaryThread()) {
            block.run()
        } else {
            runTask(plugin, block)
        }
    }

}