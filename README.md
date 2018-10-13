# Skedule
A small coroutine library for the BukkitScheduler for Bukkit/Spigot plugin developers using Kotlin

Tired of designing complex BukkitRunnables to meet your needs? Do you find yourself in [Callback Hell](http://callbackhell.com/) a tad too often?
Fret no more, for with Kotlin's coroutines and this nifty little utility, you will be scheduling tasks like never before!

## How to use Skedule?
From here on, assume the following is defined:
```kotlin
val scheduler = Bukkit.getScheduler()
```

### The simplest form
The simplest example looks like this:
```kotlin
//scheduler and plugin are assumed to be defined
scheduler.schedule(plugin) {
    waitFor(40)
    Bukkit.broadcastMessage("Waited 40 ticks")
}
```
Of course, this isn't very useful, and doesn't really showcase what Skedule is capable of.
So here is a more useful example:
```kotlin
scheduler.schedule(plugin) {
    Bukkit.broadcastMessage("Waited 0 ticks")
    waitFor(20)
    Bukkit.broadcastMessage("Waited 20 ticks")
    waitFor(20)
    Bukkit.broadcastMessage("Waited 40 ticks")
    waitFor(20)
    Bukkit.broadcastMessage("Waited 60 ticks")
}
```
This may look like procedural code that will block the main server thread, but it really isn't.
The extension method `schedule` starts a coroutine. At each of the waitFor calls the coroutine is suspended,
a task is scheduled, and the rest of the coroutine is set aside for continuation at a later point
(40 game ticks in the future in this case). After this, control is yielded back to the caller (your plugin).
From there, the server carries on doing whatever it was doing, until the 40 ticks have passed, after which
the coroutine will continue until suspended again, or finished.

### A more useful example
A great real-world example of when Skedule would be useful, is when you need a countdown of some sort.
Say you wanted to start a game countdown of 10 seconds, and each second you wanted to display the
remaining time. With Skedule, this is super easy. No need to create an entirely new class that implements
Runnable and uses mutable state to track how many seconds are left. All you have to do, is use a regular
for-loop:
```kotlin
scheduler.schedule(plugin) {
    for (i in 10 downTo 1) {
        Bukkit.broadcastMessage("Time left: $i sec...")
        waitFor(20)
    }
    Bukkit.broadcastMessage("Game starts now!")
}
```
This example really shows where Skedule is at its most powerful.

### Repeating vs non-repeating
Take a look at the examples above one more time. They all share one common drawback, and it may not be
obvious just by looking at them. At each suspension point (`waitFor`) a new task is scheduled for the delay.
Every single time. This may not be desirable in all cases. Many a time - like in the for-loop example
above - it makes much more sense to schedule a single repeating task to run over and over. In Skedule, you
can tell a coroutine to schedule a repeating task, and at each suspension point, wait until the next execution
of the task before continuing. To do this, you need to use the `repeating` method:
```kotlin
scheduler.schedule(plugin) {
    repeating(20)
    for (i in 10 downTo 1) {
        Bukkit.broadcastMessage("Time left: $i sec...")
        yield() //wait for next iteration
    }
    Bukkit.broadcastMessage("Game starts now!")
}
```
Here, we tell the coroutine to schedule a repeating task with a period of 20 ticks. `yield` is a suspension point.
Each time this it called, the coroutine will suspend until the next iteration of the repeating task, which in
our case is 20 ticks in the future. This approach imposes less of an overhead, since behind the scenes only one
task is scheduled to run repeatedly. The task will, of course, be automatically cancelled when (if ever) the
coroutine returns. It won't be left hanging.

You can also use `waitFor` in a repeating-task coroutine. The behaviour then is defined not as waiting exactly
the specified amount of ticks, but as waiting **at least** the specified amount of ticks. More specifically, calling
`waitFor(n)` will suspend the coroutine for n ticks plus the ticks remaining until the next iteration of the
repeating task. `waitFor` will also return the total amount of ticks waited. Example:
```kotlin
scheduler.schedule(plugin) {
    repeating(20)
    val waited = waitFor(45)
    Bukkit.broadcastMessage("$waited") //broadcasts "60"
}
```

### Asynchronous tasks
The Bukkit scheduler isn't all about scheduling tasks on the main game thread. We often find ourselves
having to do I/O or query a database, or we might have to do some long and costly operations. In all of
these cases, so as to not block the game thread, we want to schedule an asynchronous task. Skedule supports
this. To schedule any task with Skedule, a SynchronizationContext needs to be provided. If you do not provide
a SynchronizationContext, `SYNC` is inferred. If you want to schedule asynchronous tasks with Skedule, you
need to explicitly pass `ASYNC`:
```kotlin
scheduler.schedule(plugin, SynchronizationContext.ASYNC) {
    Bukkit.broadcastMessage("Doing some heavy work off the main thread")
    //Do costly operation
}
```
You can also switch back and forth between sync and async execution:
```kotlin
scheduler.schedule(plugin, SynchronizationContext.ASYNC) { //ASYNC here specifies the initial context
    Bukkit.broadcastMessage("Doing some heavy work off the main thread")
    //Do costly operation off the main thread
    switchContext(SynchronizationContext.SYNC)
    //Do stuff on the main thread
    switchContext(SynchronizationContext.ASYNC)
    //Do more costly stuff off the main thread
}
```

### CoroutineDispatcher
Skedule also comes with a Bukkit `CoroutineDispatcher` for use with the `kotlinx.coroutines` library. Use it like any old
`CoroutineContext`:
```kotlin
    //sync:
    launch(BukkitDispatcher(this)) {
        delay(3, TimeUnit.SECONDS)
        Bukkit.broadcastMessage("Waited for 3 seconds") //On sync scheduler thread
    }
    
    //async:
    launch(BukkitDispatcher(this, async = true)) {
        delay(3, TimeUnit.SECONDS)
        Bukkit.broadcastMessage("Waited for 3 seconds") //On async scheduler thread
    }
```

You can read more about kotlinx.coroutines here:  
https://github.com/Kotlin/kotlinx.coroutines

## Where to get Skedule
### Maven
```maven
<repositories>
    <repository>
        <id>okkero</id>
        <url>http://nexus.okkero.com/repository/maven-releases/</url>
    </repository>
</repositories>
```
```maven
<dependencies>
    <dependency>
        <groupId>com.okkero.skedule</groupId>
        <artifactId>skedule</artifactId>
        <version>1.2.4</version>
        <scope>compile</scope>
    </dependency>
</dependencies>
```

### Get the Kotlin runtime yourself
Skedule does not contain the Kotlin runtime (and the reason should be obvious).
Therefore you must make sure the runtime exists in the classpath on your server.
Skedule also uses API from kotlinx-coroutines, so make sure you have that too.

## Documentation
Coming soon. For now, if you need more formal documentation than
this readme, please find the KDoc comments in the source code.

## Not using Kotlin?
If you're not using Kotlin, this resource won't help you. There is no way to express coroutines in
Java. However, [TaskChain](https://github.com/aikar/TaskChain) has got you covered. With TaskChain
you can express your synchronous and asynchronous scheduler calls in a reactive sort of way. It
comes with a really elaborate library to make your experience smooth.

Head over to [TaskChain](https://github.com/aikar/TaskChain)
