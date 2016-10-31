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

/**
 * Created by MiniDigger on 31.10.2016.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class MockTaskChainFactory extends TaskChainFactory {
    private MockTaskChainFactory(MockScheduler scheduler) {
        super(new MockGameInterface(scheduler));
    }
    
    public static TaskChainFactory create(MockScheduler scheduler) {
        return new MockTaskChainFactory(scheduler);
    }
    
    @SuppressWarnings("PublicInnerClass")
    private static class MockGameInterface implements GameInterface {
        private final AsyncQueue asyncQueue;
        private final MockScheduler mockScheduler;
        
        MockGameInterface(MockScheduler scheduler) {
            this.mockScheduler = scheduler;
            this.asyncQueue = new TaskChainAsyncQueue();
        }
        
        @Override
        public AsyncQueue getAsyncQueue() {
            return this.asyncQueue;
        }
        
        @Override
        public boolean isMainThread() {
            return mockScheduler.isMainThread();
        }
        
        @Override
        public void postToMain(Runnable run) {
            mockScheduler.postToMain(run);
        }
        
        @Override
        public void scheduleTask(int ticks, Runnable run) {
            mockScheduler.scheduleTask(ticks, run);
        }
        
        @Override
        public void registerShutdownHandler(TaskChainFactory factory) {
            mockScheduler.registerShutdownHandler(factory);
        }
    }
}
