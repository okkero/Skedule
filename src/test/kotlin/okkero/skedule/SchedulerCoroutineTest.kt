package okkero.skedule

import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.runners.MockitoJUnitRunner
import kotlin.test.assertNotNull

@RunWith(MockitoJUnitRunner::class)
class SchedulerCoroutineTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    lateinit var server: Server
    @Mock lateinit var scheduler: BukkitScheduler
    @Mock lateinit var plugin: Plugin

    private var mockRepeatingTask: MockRepeatingTask? = null

    @Before
    fun setup() {
        `when`(scheduler.runTaskTimer(eq(plugin), any(Runnable::class.java), anyLong(), anyLong())).then {
            if (this.mockRepeatingTask != null) {
                throw Exception()
            }

            val task = mock(BukkitTask::class.java)
            val mockRepeatingTask = MockRepeatingTask { (it.arguments[1] as Runnable).run() }
            `when`(task.cancel()).then {
                mockRepeatingTask.cancelled = true
                Unit
            }
            this.mockRepeatingTask = mockRepeatingTask

            task
        }
        `when`(scheduler.runTaskLater(eq(plugin), any(Runnable::class.java), anyLong())).then {
            (it.arguments[1] as Runnable).run()
            mock(BukkitTask::class.java)
        }

        setupServerMock()
    }

    @After
    fun tearDown() {
        mockRepeatingTask = null
    }

    @Test
    fun `schedules correct tasks when not repeating`() {
        scheduler.schedule(plugin) {
            waitFor(30)
            waitFor(30)
        }
        verify(scheduler, times(2)).runTaskLater(eq(plugin), any(Runnable::class.java), eq(30L))
        verifyNoMoreInteractions(scheduler)
    }

    @Test
    fun `schedules correct tasks when repeating`() {
        scheduler.schedule(plugin) {
            repeating(20)
            yield()
            waitFor(40)
        }
        assertNotNull(mockRepeatingTask)
        mockRepeatingTask!!.startTask()
        verify(scheduler).runTaskTimer(eq(plugin), any(Runnable::class.java), anyLong(), eq(20L))
        verifyNoMoreInteractions(scheduler)
    }

    @Test
    fun `cancels bukkit task when skedule task is cancelled`() {
        var task: BukkitTask? = null
        `when`(scheduler.runTaskLater(eq(plugin), any(Runnable::class.java), anyLong())).then {
            task = mock(BukkitTask::class.java)
            task
        }
        val skeduleTask = scheduler.schedule(plugin) {
            waitFor(40)
        }

        skeduleTask.cancel()
        verify(task!!, times(1)).cancel()
    }

    private fun setupServerMock() {
        `when`(server.isPrimaryThread).thenReturn(true)
        if (Bukkit.getServer() == null) {
            Bukkit.setServer(server)
        }
    }

}

private class MockRepeatingTask(val task: () -> Unit) {

    var cancelled = false

    fun startTask() {
        while (!cancelled) {
            task()
        }
    }

}