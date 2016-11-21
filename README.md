# Warning!
This resource uses features of the early access preview of Kotlin 1.1. Jetbrains has made no guarantees of compatibility between the current EAP and the final release of 1.1. Therefore the APIs present in this resource may change between now and the release of Kotlin 1.1.

More information here:  
https://blog.jetbrains.com/kotlin/2016/07/first-glimpse-of-kotlin-1-1-coroutines-type-aliases-and-more/  
https://blog.jetbrains.com/kotlin/2016/10/kotlin-1-1-m02-is-here/  

# Skedule
A small coroutine library for the BukkitScheduler for Bukkit/Spigot plugin developers using Kotlin

Tired of designing complex BukkitRunnables to meet your needs? Do you find yourself in [Callback Hell](http://callbackhell.com/) a tad too often?
Fret no more, for with Kotlin's coroutines and this nifty little utility, you will be scheduling tasks like never before!

## How to use Skedule?
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
TODO

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

## Where to get Skedule
### Maven
TODO
### Get the Kotlin runtime yourself
Skedule does not contain the Kotlin runtime (and the reason should be obvious).
Therefore you must make sure the runtime exists in the classpath on your server.

## Documentation
As Kotlin 1.1 is still in early access, Dokka does not yet support it. As a result, there is no
nicely formatted online documentation for Skedule yet. If you need more formal documentation than
this readme, please find the KDoc comments in the source code.