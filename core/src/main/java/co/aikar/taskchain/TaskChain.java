/*
 * Copyright (c) 2016 Daniel Ennis (Aikar) - MIT License
 *
 *  Permission is hereby granted, free of charge, to any person obtaining
 *  a copy of this software and associated documentation files (the
 *  "Software"), to deal in the Software without restriction, including
 *  without limitation the rights to use, copy, modify, merge, publish,
 *  distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to
 *  the following conditions:
 *
 *  The above copyright notice and this permission notice shall be
 *  included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 *  LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 *  OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 *  WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

/*
 * TaskChain for Minecraft Plugins
 *
 * Written by Aikar <aikar@aikar.co>
 * https://aikar.co
 * https://starlis.com
 *
 * @license MIT
 */

package co.aikar.taskchain;

import co.aikar.taskchain.TaskChainTasks.AsyncExecutingFirstTask;
import co.aikar.taskchain.TaskChainTasks.AsyncExecutingGenericTask;
import co.aikar.taskchain.TaskChainTasks.AsyncExecutingTask;
import co.aikar.taskchain.TaskChainTasks.FirstTask;
import co.aikar.taskchain.TaskChainTasks.GenericTask;
import co.aikar.taskchain.TaskChainTasks.LastTask;
import co.aikar.taskchain.TaskChainTasks.Task;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;


/**
 * TaskChain v3.0 - by Daniel Ennis <aikar@aikar.co>
 *
 * Facilitates Control Flow for a game scheduler to easily jump between
 * Async and Sync execution states without deeply nested callbacks,
 * passing the response of the previous task to the next task to use.
 *
 * Also can be used to guarantee execution order to 2 ensure
 * that 2 related actions can never run at same time, and 1 set of tasks
 * will not start executing until the previous set is finished.
 *
 *
 * Find latest updates at https://taskchain.emc.gs
 */
@SuppressWarnings({"unused", "FieldAccessedSynchronizedAndUnsynchronized"})
public class TaskChain <T> {
    private static final ThreadLocal<TaskChain<?>> currentChain = new ThreadLocal<>();

    private final boolean shared;
    private final GameInterface impl;
    private final TaskChainFactory factory;
    private final String sharedName;
    private final Map<String, Object> taskMap = new HashMap<>(0);
    private final ConcurrentLinkedQueue<TaskHolder<?,?>> chainQueue = new ConcurrentLinkedQueue<>();

    private boolean executed = false;
    private boolean async;
    private int actionIndex;
    private int currentActionIndex;
    boolean done = false;

    private Object previous;
    private TaskHolder<?, ?> currentHolder;
    private Consumer<Boolean> doneCallback;
    private BiConsumer<Exception, Task<?, ?>> errorHandler;

    /* ======================================================================================== */
    TaskChain(TaskChainFactory factory) {
        this(factory, false, null);
    }

    TaskChain(TaskChainFactory factory, boolean shared, String sharedName) {
        this.factory = factory;
        this.impl = factory.getImplementation();
        this.shared = shared;
        this.sharedName = sharedName;
    }
    /* ======================================================================================== */
    // <editor-fold desc="// Getters & Setters">

    /**
     * Called in an executing task, get the current action index.
     * For every action that adds a task to the chain, the action index is increased.
     *
     * Useful in error or done handlers to know where you are in the chain when it aborted or threw exception.
     * @return The current index
     */
    public int getCurrentActionIndex() {
        return currentActionIndex;
    }

    /**
     * Changes the done callback handler for this chain
     * @param doneCallback The handler
     */
    @SuppressWarnings("WeakerAccess")
    public void setDoneCallback(Consumer<Boolean> doneCallback) {
        this.doneCallback = doneCallback;
    }

    /**
     * @return The current error handler or null
     */
    public BiConsumer<Exception, Task<?, ?>> getErrorHandler() {
        return errorHandler;
    }

    /**
     * Changes the error handler for this chain
     * @param errorHandler The error handler
     */
    @SuppressWarnings("WeakerAccess")
    public void setErrorHandler(BiConsumer<Exception, Task<?, ?>> errorHandler) {
        this.errorHandler = errorHandler;
    }
    // </editor-fold>
    /* ======================================================================================== */
    //<editor-fold desc="// API Methods">
    /**
     * Call to abort execution of the chain. Should be called inside of an executing task.
     */
    @SuppressWarnings("WeakerAccess")
    public static void abort() {
        TaskChainUtil.sneakyThrows(new AbortChainException());
    }

    /**
     * Usable only inside of an executing Task or Chain Error/Done handlers
     *
     * Gets the current chain that is executing this Task or Error/Done handler
     * This method should only be called on the same thread that is executing the method.
     *
     * In an AsyncExecutingTask, You must call this method BEFORE passing control to another thread.
     */
    @SuppressWarnings("WeakerAccess")
    public static TaskChain<?> getCurrentChain() {
        return currentChain.get();
    }

    /* ======================================================================================== */

    /**
     * Checks if the chain has a value saved for the specified key.
     * @param key Key to check if Task Data has a value for
     */
    @SuppressWarnings("WeakerAccess")
    public boolean hasTaskData(String key) {
        return taskMap.containsKey(key);
    }

    /**
     * Retrieves a value relating to a specific key, saved by a previous task.
     *
     * @param key Key to look up Task Data for
     * @param <R> Type the Task Data value is expected to be
     */
    @SuppressWarnings("WeakerAccess")
    public <R> R getTaskData(String key) {
        //noinspection unchecked
        return (R) taskMap.get(key);
    }

    /**
     * Saves a value for this chain so that a task furthur up the chain can access it.
     *
     * Useful for passing multiple values to the next (or furthur) tasks.
     *
     * @param key Key to store in Task Data
     * @param val Value to store in Task Data
     * @param <R> Type the Task Data value is expected to be
     */
    @SuppressWarnings("WeakerAccess")
    public <R> R setTaskData(String key, Object val) {
        //noinspection unchecked
        return (R) taskMap.put(key, val);
    }

    /**
     * Removes a saved value on the chain.
     *
     * @param key Key to remove from Task Data
     * @param <R> Type the Task Data value is expected to be
     */
    @SuppressWarnings("WeakerAccess")
    public <R> R removeTaskData(String key) {
        //noinspection unchecked
        return (R) taskMap.remove(key);
    }

    /**
     * Takes the previous tasks return value, stores it to the specified key
     * as Task Data, and then forwards that value to the next task.
     *
     * @param key Key to store the previous return value into Task Data
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<T> storeAsData(String key) {
        return current((val) -> {
            setTaskData(key, val);
            return val;
        });
    }

    /**
     * Reads the specified key from Task Data, and passes it to the next task.
     *
     * Will need to pass expected type such as chain.<Foo>returnData("key")
     *
     * @param key Key to retrieve from Task Data and pass to next task
     * @param <R> Return type that the next parameter can expect as argument type
     */
    @SuppressWarnings("WeakerAccess")
    public <R> TaskChain<R> returnData(String key) {
        //noinspection unchecked
        return currentFirst(() -> (R) getTaskData(key));
    }

    /**
     * Returns the chain itself to the next task.
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<TaskChain<?>> returnChain() {
        return currentFirst(() -> this);
    }

    /**
     * Checks if the previous task return was null.
     *
     * If not null, the previous task return will forward to the next task.
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<T> abortIfNull() {
        return abortIfNull(null, null, null, null);
    }

    /**
     * {@link #abortIf(T, TaskChainAbortAction, Object, Object, Object)}
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<T> abortIfNull(TaskChainAbortAction<?, ?, ?> action) {
        return abortIf(null, action, null, null, null);
    }

    /**
     * {@link #abortIf(T, TaskChainAbortAction, Object, Object, Object)}
     */
    @SuppressWarnings("WeakerAccess")
    public <A1> TaskChain<T> abortIfNull(TaskChainAbortAction<A1, ?, ?> action, A1 arg1) {
        //noinspection unchecked
        return abortIf(null, action, arg1, null, null);
    }

    /**
     * {@link #abortIf(T, TaskChainAbortAction, Object, Object, Object)}
     */
    @SuppressWarnings("WeakerAccess")
    public <A1, A2> TaskChain<T> abortIfNull(TaskChainAbortAction<A1, A2, ?> action, A1 arg1, A2 arg2) {
        //noinspection unchecked
        return abortIf(null, action, arg1, arg2, null);
    }

    /**
     * Checks if the previous task return was null, and aborts if it was
     * Then executes supplied action handler
     *
     * If not null, the previous task return will forward to the next task.
     */
    @SuppressWarnings("WeakerAccess")
    public <A1, A2, A3> TaskChain<T> abortIfNull(TaskChainAbortAction<A1, A2, A3> action, A1 arg1, A2 arg2, A3 arg3) {
        //noinspection unchecked
        return abortIf(null, action, arg1, arg2, arg3);
    }

    /**
     * Checks if the previous task return is the supplied value.
     *
     * If not, the previous task return will forward to the next task.
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<T> abortIf(T ifObj) {
        return abortIf(ifObj, null, null, null, null);
    }

    /**
     * {@link #abortIf(Object, TaskChainAbortAction, Object, Object, Object)}
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<T> abortIf(T ifObj, TaskChainAbortAction<?, ?, ?> action) {
        return abortIf(ifObj, action, null, null, null);
    }

    /**
     * {@link #abortIf(Object, TaskChainAbortAction, Object, Object, Object)}
     */
    @SuppressWarnings("WeakerAccess")
    public <A1> TaskChain<T> abortIf(T ifObj, TaskChainAbortAction<A1, ?, ?> action, A1 arg1) {
        return abortIf(ifObj, action, arg1, null, null);
    }

    /**
     * {@link #abortIf(Object, TaskChainAbortAction, Object, Object, Object)}
     */
    @SuppressWarnings("WeakerAccess")
    public <A1, A2> TaskChain<T> abortIf(T ifObj, TaskChainAbortAction<A1, A2, ?> action, A1 arg1, A2 arg2) {
        return abortIf(ifObj, action, arg1, arg2, null);
    }

    /**
     * Checks if the previous task return is the supplied value, and aborts if it was.
     * Then executes supplied action handler
     *
     * If not null, the previous task return will forward to the next task.
     */
    @SuppressWarnings("WeakerAccess")
    public <A1, A2, A3> TaskChain<T> abortIf(T ifObj, TaskChainAbortAction<A1, A2, A3> action, A1 arg1, A2 arg2, A3 arg3) {
        return current((obj) -> {
            if (Objects.equals(obj, ifObj)) {
                handleAbortAction(action, arg1, arg2, arg3);
                return null;
            }
            return obj;
        });
    }

    /**
     * Checks if the previous task return is not the supplied value.
     *
     * If it is, the previous task return will forward to the next task.
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<T> abortIfNot(T ifNotObj) {
        return abortIfNot(ifNotObj, null, null, null, null);
    }

    /**
     * {@link #abortIfNot(Object, TaskChainAbortAction, Object, Object, Object)}
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<T> abortIfNot(T ifNotObj, TaskChainAbortAction<?, ?, ?> action) {
        return abortIfNot(ifNotObj, action, null, null, null);
    }

    /**
     * {@link #abortIfNot(Object, TaskChainAbortAction, Object, Object, Object)}
     */
    @SuppressWarnings("WeakerAccess")
    public <A1> TaskChain<T> abortIfNot(T ifNotObj, TaskChainAbortAction<A1, ?, ?> action, A1 arg1) {
        return abortIfNot(ifNotObj, action, arg1, null, null);
    }

    /**
     * {@link #abortIfNot(Object, TaskChainAbortAction, Object, Object, Object)}
     */
    @SuppressWarnings("WeakerAccess")
    public <A1, A2> TaskChain<T> abortIfNot(T ifNotObj, TaskChainAbortAction<A1, A2, ?> action, A1 arg1, A2 arg2) {
        return abortIfNot(ifNotObj, action, arg1, arg2, null);
    }

    /**
     * Checks if the previous task return is the supplied value, and aborts if it was.
     * Then executes supplied action handler
     *
     * If not null, the previous task return will forward to the next task.
     */
    @SuppressWarnings("WeakerAccess")
    public <A1, A2, A3> TaskChain<T> abortIfNot(T ifNotObj, TaskChainAbortAction<A1, A2, A3> action, A1 arg1, A2 arg2, A3 arg3) {
        return current((obj) -> {
            if (!Objects.equals(obj, ifNotObj)) {
                handleAbortAction(action, arg1, arg2, arg3);
                return null;
            }
            return obj;
        });
    }

    /**
     * IMPLEMENTATION SPECIFIC!!
     * Consult your application implementation to understand how long 1 unit is.
     *
     * For example, in Minecraft it is a tick, which is roughly 50 milliseconds, but not guaranteed.
     *
     * Adds a delay to the chain execution.
     *
     * @param gameUnits # of game units to delay before next task
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<T> delay(final int gameUnits) {
        //noinspection CodeBlock2Expr
        return currentCallback((input, next) -> {
            impl.scheduleTask(gameUnits, () -> next.accept(input));
        });
    }

    /**
     * Adds a real time delay to the chain execution.
     * Chain will abort if the delay is interrupted.
     *
     * @param duration duration of the delay before next task
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<T> delay(final int duration, TimeUnit unit) {
        //noinspection CodeBlock2Expr
        return currentCallback((input, next) -> {
            impl.scheduleTask(duration, unit, () -> next.accept(input));
        });
    }

    /**
     * Execute a task on the main thread, with no previous input, and a callback to return the response to.
     *
     * It's important you don't perform blocking operations in this method. Only use this if
     * the task will be scheduling a different sync operation outside of the TaskChains scope.
     *
     * Usually you could achieve the same design with a blocking API by switching to an async task
     * for the next task and running it there.
     *
     * This method would primarily be for cases where you need to use an API that ONLY provides
     * a callback style API.
     *
     * @param task The task to execute
     * @param <R> Return type that the next parameter can expect as argument type
     */
    @SuppressWarnings("WeakerAccess")
    public <R> TaskChain<R> syncFirstCallback(AsyncExecutingFirstTask<R> task) {
        //noinspection unchecked
        return add0(new TaskHolder<>(this, false, task));
    }

    /**
     * @see #syncFirstCallback(AsyncExecutingFirstTask) but ran off main thread
     * @param task The task to execute
     * @param <R> Return type that the next parameter can expect as argument type
     */
    @SuppressWarnings("WeakerAccess")
    public <R> TaskChain<R> asyncFirstCallback(AsyncExecutingFirstTask<R> task) {
        //noinspection unchecked
        return add0(new TaskHolder<>(this, true, task));
    }

    /**
     * @see #syncFirstCallback(AsyncExecutingFirstTask) but ran on current thread the Chain was created on
     * @param task The task to execute
     * @param <R> Return type that the next parameter can expect as argument type
     */
    @SuppressWarnings("WeakerAccess")
    public <R> TaskChain<R> currentFirstCallback(AsyncExecutingFirstTask<R> task) {
        //noinspection unchecked
        return add0(new TaskHolder<>(this, null, task));
    }

    /**
     * Execute a task on the main thread, with the last output, and a callback to return the response to.
     *
     * It's important you don't perform blocking operations in this method. Only use this if
     * the task will be scheduling a different sync operation outside of the TaskChains scope.
     *
     * Usually you could achieve the same design with a blocking API by switching to an async task
     * for the next task and running it there.
     *
     * This method would primarily be for cases where you need to use an API that ONLY provides
     * a callback style API.
     *
     * @param task The task to execute
     * @param <R> Return type that the next parameter can expect as argument type
     */
    @SuppressWarnings("WeakerAccess")
    public <R> TaskChain<R> syncCallback(AsyncExecutingTask<R, T> task) {
        //noinspection unchecked
        return add0(new TaskHolder<>(this, false, task));
    }

    /**
     * @see #syncCallback(AsyncExecutingTask), ran on main thread but no input or output
     * @param task The task to execute
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<?> syncCallback(AsyncExecutingGenericTask task) {
        return add0(new TaskHolder<>(this, false, task));
    }

    /**
     * @see #syncCallback(AsyncExecutingTask) but ran off main thread
     * @param task The task to execute
     * @param <R> Return type that the next parameter can expect as argument type
     */
    @SuppressWarnings("WeakerAccess")
    public <R> TaskChain<R> asyncCallback(AsyncExecutingTask<R, T> task) {
        //noinspection unchecked
        return add0(new TaskHolder<>(this, true, task));
    }

    /**
     * @see #syncCallback(AsyncExecutingTask) but ran off main thread
     * @param task The task to execute
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<?> asyncCallback(AsyncExecutingGenericTask task) {
        return add0(new TaskHolder<>(this, true, task));
    }

    /**
     * @see #syncCallback(AsyncExecutingTask) but ran on current thread the Chain was created on
     * @param task The task to execute
     * @param <R> Return type that the next parameter can expect as argument type
     */
    @SuppressWarnings("WeakerAccess")
    public <R> TaskChain<R> currentCallback(AsyncExecutingTask<R, T> task) {
        //noinspection unchecked
        return add0(new TaskHolder<>(this, null, task));
    }

    /**
     * @see #syncCallback(AsyncExecutingTask) but ran on current thread the Chain was created on
     * @param task The task to execute
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<?> currentCallback(AsyncExecutingGenericTask task) {
        return add0(new TaskHolder<>(this, null, task));
    }

    /**
     * Execute task on main thread, with no input, returning an output
     * @param task The task to execute
     * @param <R> Return type that the next parameter can expect as argument type
     */
    @SuppressWarnings("WeakerAccess")
    public <R> TaskChain<R> syncFirst(FirstTask<R> task) {
        //noinspection unchecked
        return add0(new TaskHolder<>(this, false, task));
    }

    /**
     * @see #syncFirst(FirstTask) but ran off main thread
     * @param task The task to execute
     * @param <R> Return type that the next parameter can expect as argument type
     */
    @SuppressWarnings("WeakerAccess")
    public <R> TaskChain<R> asyncFirst(FirstTask<R> task) {
        //noinspection unchecked
        return add0(new TaskHolder<>(this, true, task));
    }

    /**
     * @see #syncFirst(FirstTask) but ran on current thread the Chain was created on
     * @param task The task to execute
     * @param <R> Return type that the next parameter can expect as argument type
     */
    @SuppressWarnings("WeakerAccess")
    public <R> TaskChain<R> currentFirst(FirstTask<R> task) {
        //noinspection unchecked
        return add0(new TaskHolder<>(this, null, task));
    }

    /**
     * Execute task on main thread, with the last returned input, returning an output
     * @param task The task to execute
     * @param <R> Return type that the next parameter can expect as argument type
     */
    @SuppressWarnings("WeakerAccess")
    public <R> TaskChain<R> sync(Task<R, T> task) {
        //noinspection unchecked
        return add0(new TaskHolder<>(this, false, task));
    }

    /**
     * Execute task on main thread, with no input or output
     * @param task The task to execute
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<?> sync(GenericTask task) {
        return add0(new TaskHolder<>(this, false, task));
    }

    /**
     * @see #sync(Task) but ran off main thread
     * @param task The task to execute
     * @param <R> Return type that the next parameter can expect as argument type
     */
    @SuppressWarnings("WeakerAccess")
    public <R> TaskChain<R> async(Task<R, T> task) {
        //noinspection unchecked
        return add0(new TaskHolder<>(this, true, task));
    }

    /**
     * @see #sync(GenericTask) but ran off main thread
     * @param task The task to execute
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<?> async(GenericTask task) {
        return add0(new TaskHolder<>(this, true, task));
    }

    /**
     * @see #sync(Task) but ran on current thread the Chain was created on
     * @param task The task to execute
     * @param <R> Return type that the next parameter can expect as argument type
     */
    @SuppressWarnings("WeakerAccess")
    public <R> TaskChain<R> current(Task<R, T> task) {
        //noinspection unchecked
        return add0(new TaskHolder<>(this, null, task));
    }

    /**
     * @see #sync(GenericTask) but ran on current thread the Chain was created on
     * @param task The task to execute
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<?> current(GenericTask task) {
        return add0(new TaskHolder<>(this, null, task));
    }


    /**
     * Execute task on main thread, with the last output, and no furthur output
     * @param task The task to execute
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<?> syncLast(LastTask<T> task) {
        return add0(new TaskHolder<>(this, false, task));
    }

    /**
     * @see #syncLast(LastTask) but ran off main thread
     * @param task The task to execute
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<?> asyncLast(LastTask<T> task) {
        return add0(new TaskHolder<>(this, true, task));
    }

    /**
     * @see #syncLast(LastTask) but ran on current thread the Chain was created on
     * @param task The task to execute
     */
    @SuppressWarnings("WeakerAccess")
    public TaskChain<?> currentLast(LastTask<T> task) {
        return add0(new TaskHolder<>(this, null, task));
    }

    /**
     * Finished adding tasks, begins executing them.
     */
    @SuppressWarnings("WeakerAccess")
    public void execute() {
        execute((Consumer<Boolean>) null, null);
    }

    /**
     * Finished adding tasks, begins executing them with a done notifier
     * @param done The Callback to handle when the chain has finished completion. Argument to consumer contains finish state
     */
    @SuppressWarnings("WeakerAccess")
    public void execute(Runnable done) {
        execute((finished) -> done.run(), null);
    }

    /**
     * Finished adding tasks, begins executing them with a done notifier and error handler
     * @param done The Callback to handle when the chain has finished completion. Argument to consumer contains finish state
     * @param errorHandler The Error handler to handle exceptions
     */
    @SuppressWarnings("WeakerAccess")
    public void execute(Runnable done, BiConsumer<Exception, Task<?, ?>> errorHandler) {
        execute((finished) -> done.run(), errorHandler);
    }

    /**
     * Finished adding tasks, with a done notifier
     * @param done The Callback to handle when the chain has finished completion. Argument to consumer contains finish state
     */
    @SuppressWarnings("WeakerAccess")
    public void execute(Consumer<Boolean> done) {
        execute(done, null);
    }

    /**
     * Finished adding tasks, begins executing them, with an error handler
     * @param errorHandler The Error handler to handle exceptions
     */
    public void execute(BiConsumer<Exception, Task<?, ?>> errorHandler) {
        execute((Consumer<Boolean>) null, errorHandler);
    }

    /**
     * Finished adding tasks, begins executing them with a done notifier and error handler
     * @param done The Callback to handle when the chain has finished completion. Argument to consumer contains finish state
     * @param errorHandler The Error handler to handle exceptions
     */
    public void execute(Consumer<Boolean> done, BiConsumer<Exception, Task<?, ?>> errorHandler) {
        if (errorHandler == null) {
            errorHandler = factory.getDefaultErrorHandler();
        }
        this.doneCallback = done;
        this.errorHandler = errorHandler;
        execute0();
    }

    // </editor-fold>
    /* ======================================================================================== */
    //<editor-fold desc="// Implementation Details">
    private <A1, A2, A3> void handleAbortAction(TaskChainAbortAction<A1, A2, A3> action, A1 arg1, A2 arg2, A3 arg3) {
        if (action != null) {
            final TaskChain<?> prev = currentChain.get();
            try {
                currentChain.set(this);
                action.onAbort(this, arg1, arg2, arg3);
            } catch (Exception e) {
                TaskChainUtil.logError("TaskChain Exception in Abort Action handler: " + action.getClass().getName());
                TaskChainUtil.logError("Current Action Index was: " + currentActionIndex);
                e.printStackTrace();
            } finally {
                currentChain.set(prev);
            }
        }
        abort();
    }

    void execute0() {
        synchronized (this) {
            if (this.executed) {
                if (this.shared) {
                    return;
                }
                throw new RuntimeException("Already executed");
            }
            this.executed = true;
        }
        async = !impl.isMainThread();
        nextTask();
    }

    void done(boolean finished) {
        this.done = true;
        if (this.shared) {
            factory.removeSharedChain(this.sharedName);
        }
        if (this.doneCallback != null) {
            final TaskChain<?> prev = currentChain.get();
            try {
                currentChain.set(this);
                this.doneCallback.accept(finished);
            } catch (Exception e) {
                this.handleError(e, null);
            } finally {
                currentChain.set(prev);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "WeakerAccess"})
    protected TaskChain add0(TaskHolder<?,?> task) {
        synchronized (this) {
            if (!this.shared && this.executed) {
                throw new RuntimeException("TaskChain is executing");
            }
        }

        this.chainQueue.add(task);
        return this;
    }

    /**
     * Fires off the next task, and switches between Async/Sync as necessary.
     */
    private void nextTask() {
        synchronized (this) {
            this.currentHolder = this.chainQueue.poll();
            if (this.currentHolder == null) {
                this.done = true; // to ensure its done while synchronized
            }
        }

        if (this.currentHolder == null) {
            this.previous = null;
            // All Done!
            this.done(true);
            return;
        }

        Boolean isNextAsync = this.currentHolder.async;
        if (isNextAsync == null || factory.shutdown) {
            this.currentHolder.run();
        } else if (isNextAsync) {
            if (this.async) {
                this.currentHolder.run();
            } else {
                impl.postAsync(() -> {
                    this.async = true;
                    this.currentHolder.run();
                });
            }
        } else {
            if (this.async) {
                impl.postToMain(() -> {
                    this.async = false;
                    this.currentHolder.run();
                });
            } else {
                this.currentHolder.run();
            }
        }
    }

    private void handleError(Exception e, Task<?, ?> task) {
        if (errorHandler != null) {
            final TaskChain<?> prev = currentChain.get();
            try {
                currentChain.set(this);
                errorHandler.accept(e, task);
            } catch (Exception e2) {
                TaskChainUtil.logError("TaskChain Exception in the error handler!" + e2.getMessage());
                TaskChainUtil.logError("Current Action Index was: " + currentActionIndex);
                e.printStackTrace();
            } finally {
                currentChain.set(prev);
            }
        } else {
            TaskChainUtil.logError("TaskChain Exception on " + (task != null ? task.getClass().getName() : "Done Hander") + ": " + e.getMessage());
            TaskChainUtil.logError("Current Action Index was: " + currentActionIndex);
            e.printStackTrace();
        }
    }
    // </editor-fold>
    /* ======================================================================================== */
    //<editor-fold desc="// TaskHolder">
    /**
     * Provides foundation of a task with what the previous task type should return
     * to pass to this and what this task will return.
     * @param <R> Return Type
     * @param <A> Argument Type Expected
     */
    @SuppressWarnings("AccessingNonPublicFieldOfAnotherObject")
    private class TaskHolder<R, A> {
        private final TaskChain<?> chain;
        private final Task<R, A> task;
        final Boolean async;

        private boolean executed = false;
        private boolean aborted = false;
        private final int actionIndex;

        private TaskHolder(TaskChain<?> chain, Boolean async, Task<R, A> task) {
            this.actionIndex = TaskChain.this.actionIndex++;
            this.task = task;
            this.chain = chain;
            this.async = async;
        }

        /**
         * Called internally by Task Chain to facilitate executing the task and then the next task.
         */
        private void run() {
            final Object arg = this.chain.previous;
            this.chain.previous = null;
            TaskChain.this.currentActionIndex = this.actionIndex;
            final R res;
            final TaskChain<?> prevChain = currentChain.get();
            try {
                currentChain.set(this.chain);
                if (this.task instanceof AsyncExecutingTask) {
                    //noinspection unchecked
                    ((AsyncExecutingTask<R, A>) this.task).runAsync((A) arg, this::next);
                } else {
                    //noinspection unchecked
                    next(this.task.run((A) arg));
                }
            } catch (Exception e) {
                //noinspection ConstantConditions
                if (e instanceof AbortChainException) {
                    this.abort();
                    return;
                }
                this.chain.handleError(e, this.task);
                this.abort();
            } finally {
                if (prevChain != null) {
                    currentChain.set(prevChain);
                } else {
                    currentChain.remove();
                }
            }
        }

        /**
         * Abort the chain, and clear tasks for GC.
         */
        private synchronized void abort() {
            this.aborted = true;
            this.chain.previous = null;
            this.chain.chainQueue.clear();
            this.chain.done(false);
        }

        /**
         * Accepts result of previous task and executes the next
         */
        private void next(Object resp) {
            synchronized (this) {
                if (this.aborted) {
                    this.chain.done(false);
                    return;
                }
                if (this.executed) {
                    this.chain.done(false);
                    throw new RuntimeException("This task has already been executed.");
                }
                this.executed = true;
            }

            this.chain.async = !TaskChain.this.impl.isMainThread(); // We don't know where the task called this from.
            this.chain.previous = resp;
            this.chain.nextTask();
        }
    }
    //</editor-fold>
}
