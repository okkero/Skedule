package okkero.spigotutils.extensions.scheduler

import okkero.spigotutils.extensions.scheduler.SynchronizationContext.*
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask

/**
 * Schedule a coroutine with the Bukkit Scheduler.
 *
 * @receiver The BukkitScheduler instance to use for scheduling tasks.
 * @param plugin The Plugin instance to use for scheduling tasks.
 * @param initialContext The initial synchronization context to start off the coroutine with. See
 * [SynchronizationContext].
 *
 * @see SynchronizationContext
 */
fun BukkitScheduler.schedule(plugin: Plugin, initialContext: SynchronizationContext = SYNC,
                             coroutine co: BukkitSchedulerController.() -> Continuation<Unit>) {
    val controller = BukkitSchedulerController(plugin, this)
    controller.start(initialContext, controller.co())
}

/**
 * Controller for Bukkit Scheduler coroutine
 *
 * @property plugin the Plugin instance to schedule the tasks bound to this coroutine
 * @property scheduler the BukkitScheduler instance to schedule the tasks bound to this coroutine
 * @property currentTask the task that is currently executing within the context of this coroutine
 * @property isRepeating whether this coroutine is currently backed by a repeating task
 */
//TODO has to be thread safe
class BukkitSchedulerController(val plugin: Plugin, val scheduler: BukkitScheduler) {

    private var schedulerDelegate: TaskScheduler = NonRepeatingTaskScheduler(plugin, scheduler)

    val currentTask: BukkitTask?
        get() = schedulerDelegate.currentTask
    val isRepeating: Boolean
        get() = schedulerDelegate is RepeatingTaskScheduler


    internal fun start(initialContext: SynchronizationContext, cont: Continuation<Unit>) {
        schedulerDelegate.doContextSwitch(initialContext, { cont.resume(Unit) })
    }

    /**
     * Wait for __at least__ the specified amount of ticks. If the coroutine is currently backed by a non-repeating
     * task, a new Bukkit task will be scheduled to run the specified amount of ticks later. If this coroutine is
     * currently backed by a repeating task, the amount of ticks waited depends on the repetition resolution of the
     * coroutine. For example, if the repetition resolution is `10` and the `ticks` argument is `12`, it will result in
     * a delay of `20` ticks.
     *
     * @param ticks the amount of ticks to __at least__ wait for
     *
     * @return the actual amount of ticks waited
     */
    suspend fun waitFor(ticks: Long, cont: Continuation<Long>) {
        schedulerDelegate.doWait(ticks, cont::resume)
    }

    /**
     * Relinquish control for as short an amount of time as possible. That is, wait for as few ticks as possible.
     * If this coroutine is currently backed by a non-repeating task, this will result in a task running at the next
     * possible occasion. If this coroutine is currently backed by a repeating task, this will result in a delay for as
     * short an amount of ticks as the repetition resolution allows.
     *
     * @return the actual amount of ticks waited
     */
    suspend fun yield(cont: Continuation<Long>) {
        schedulerDelegate.doYield(cont::resume)
    }

    /**
     * Switch to the specified SynchronizationContext. If this coroutine is already in the given context, this method
     * does nothing and returns immediately. Otherwise, the behaviour is documented in [newContext].
     *
     * @param context the context to switch to
     * @return `true` if a context switch was made, `false` otherwise
     */
    suspend fun switchContext(context: SynchronizationContext, cont: Continuation<Boolean>) {
        schedulerDelegate.doContextSwitch(context, cont::resume)
    }

    /**
     * Force a new task to be scheduled in the specified context. This method will result in a new repeating or
     * non-repeating task to be scheduled. Repetition state and resolution is determined by the currently running task.
     *
     * @param context the synchronization context of the new task
     */
    suspend fun newContext(context: SynchronizationContext, cont: Continuation<Unit>) {
        schedulerDelegate.forceNewContext(context, { cont.resume(Unit) })
    }

    /**
     * Turn this coroutine into a repeating coroutine. This method will result in a new repeating task being scheduled.
     * The new task's interval will be the same as the specified resolution. Subsequent calls to [waitFor] and [yield]
     * will from here on out defer further execution to the next iteration of the repeating task. This is useful for
     * things like countdowns and delays at fixed intervals, since [waitFor] will not result in a new task being
     * spawned.
     */
    suspend fun repeating(resolution: Long, cont: Continuation<Long>) {
        schedulerDelegate = RepeatingTaskScheduler(resolution, plugin, scheduler)
        schedulerDelegate.forceNewContext(currentContext()) { cont.resume(0) }
    }

    operator fun handleResult(result: Unit, cont: Continuation<Nothing>) {
        currentTask?.cancel()
    }

    operator fun handleException(e: Throwable, cont: Continuation<Nothing>) {
        currentTask?.cancel()
        throw e
    }

}

/**
 * Represents a synchronization context that a BukkitScheduler coroutine is currently in.
 */
enum class SynchronizationContext {

    /**
     * The coroutine is in synchronous context, and all tasks are scheduled on the main server thread
     */
    SYNC,
    /**
     * The coroutine is in asynchronous context, and all tasks are scheduled asynchronously from the main server thread.
     */
    ASYNC

}


private class RepetitionContinuation(val resume: (Long) -> Unit, val delay: Long = 0) {

    var passedTicks = 0L
    private var resumed = false

    fun tryResume(passedTicks: Long) {
        if (resumed) {
            throw IllegalStateException("Already resumed")
        }
        this.passedTicks += passedTicks
        if (this.passedTicks >= delay) {
            resumed = true
            resume(this.passedTicks)
        }
    }

}

private interface TaskScheduler {

    val currentTask: BukkitTask?

    fun doWait(ticks: Long, task: (Long) -> Unit)

    fun doYield(task: (Long) -> Unit)

    fun doContextSwitch(context: SynchronizationContext, task: (Boolean) -> Unit)

    fun forceNewContext(context: SynchronizationContext, task: () -> Unit)

}

private class NonRepeatingTaskScheduler(val plugin: Plugin, val scheduler: BukkitScheduler) : TaskScheduler {

    override var currentTask: BukkitTask? = null

    override fun doWait(ticks: Long, task: (Long) -> Unit) {
        runTaskLater(ticks) { task(ticks) }
    }

    override fun doYield(task: (Long) -> Unit) {
        doWait(0, task)
    }

    //TODO Be lazy if not yet started
    override fun doContextSwitch(context: SynchronizationContext, task: (Boolean) -> Unit) {
        val currentContext = currentContext()
        if (context == currentContext) {
            task(false)
        } else {
            forceNewContext(context) { task(true) }
        }
    }

    override fun forceNewContext(context: SynchronizationContext, task: () -> Unit) {
        runTask(context) { task() }
    }

    private fun runTask(context: SynchronizationContext = currentContext(), task: () -> Unit) {
        currentTask = when (context) {
            SYNC -> scheduler.runTask(plugin, task)
            ASYNC -> scheduler.runTaskAsynchronously(plugin, task)
        }
    }

    private fun runTaskLater(ticks: Long, context: SynchronizationContext = currentContext(), task: () -> Unit) {
        currentTask = when (context) {
            SYNC -> scheduler.runTaskLater(plugin, task, ticks)
            ASYNC -> scheduler.runTaskLaterAsynchronously(plugin, task, ticks)
        }
    }

}

private class RepeatingTaskScheduler(
        val interval: Long,
        val plugin: Plugin,
        val scheduler: BukkitScheduler
) : TaskScheduler {

    override var currentTask: BukkitTask? = null
    private var nextContinuation: RepetitionContinuation? = null

    override fun doWait(ticks: Long, task: (Long) -> Unit) {
        nextContinuation = RepetitionContinuation(task, ticks)
    }

    override fun doYield(task: (Long) -> Unit) {
        nextContinuation = RepetitionContinuation(task)
    }

    //TODO Be lazy if not yet started...maybe?
    override fun doContextSwitch(context: SynchronizationContext, task: (Boolean) -> Unit) {
        val currentContext = currentContext()
        if (context == currentContext) {
            task(false)
        } else {
            forceNewContext(context) { task(true) }
        }
    }

    override fun forceNewContext(context: SynchronizationContext, task: () -> Unit) {
        doYield { task() }
        runTaskTimer(context)
    }

    private fun runTaskTimer(context: SynchronizationContext) {
        currentTask?.cancel()
        val task: () -> Unit = { nextContinuation?.tryResume(interval) }
        currentTask = when (context) {
            SYNC -> scheduler.runTaskTimer(plugin, task, 0L, interval)
            ASYNC -> scheduler.runTaskTimerAsynchronously(plugin, task, 0L, interval)
        }
    }

}

private fun currentContext() = if (Bukkit.isPrimaryThread()) SYNC else ASYNC