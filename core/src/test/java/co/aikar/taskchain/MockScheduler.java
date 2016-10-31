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

package co.aikar.taskchain;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by Martin on 31.10.2016.
 */
public class MockScheduler {
    
    public static final int SLEEP_DELAY = 10;
    
    private List<Runnable> shutdownHandlers;
    private boolean running;
    private Thread mainThread;
    private int currentTick = -1;
    private Queue<Task> pending = new PriorityBlockingQueue<>(10, (o1, o2) -> (int) (o1.nextRun - o2.nextRun));
    
    public MockScheduler() {
        this.shutdownHandlers = new ArrayList<>();
        
        start();
    }
    
    public boolean isMainThread() {
        return Thread.currentThread().equals(mainThread);
    }
    
    public void postToMain(Runnable run) {
        pending.add(new Task(currentTick, run));
    }
    
    public void scheduleTask(int ticks, Runnable run) {
        pending.add(new Task(currentTick + ticks, run));
    }
    
    public void registerShutdownHandler(TaskChainFactory factory) {
        shutdownHandlers.add(() -> factory.shutdown(60, TimeUnit.SECONDS));
    }
    
    public void shutdown() {
        running = false;
        shutdownHandlers.forEach(Runnable::run);
    }
    
    public void start() {
        running = true;
        mainThread = new Thread() {
            @Override
            public void run() {
                while (running) {
                    currentTick++;
                    System.out.println("tick " + currentTick);
                    
                    while (!pending.isEmpty() && pending.peek().nextRun <= currentTick) {
                        pending.remove().run.run();
                    }
                    
                    try {
                        Thread.sleep(SLEEP_DELAY);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        
        mainThread.start();
    }
    
    class Task {
        int nextRun;
        Runnable run;
        
        Task(int nextRun, Runnable run) {
            this.nextRun = nextRun;
            this.run = run;
        }
    }
}
