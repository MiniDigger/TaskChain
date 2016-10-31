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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static co.aikar.taskchain.MockScheduler.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * Created by Martin on 31.10.2016.
 */
public class SchedulerTest {
    
    private MockScheduler mockScheduler;
    
    @Before
    public void before() {
        mockScheduler = new MockScheduler();
    }
    
    @After
    public void after() {
        mockScheduler.shutdown();
    }
    
    @Test
    public void testPostToMain() throws InterruptedException {
        List<String> strings = Collections.synchronizedList(new ArrayList<>());
        mockScheduler.postToMain(() -> {
            System.out.println("test1");
            strings.add("test1");
        });
        mockScheduler.postToMain(() -> {
            System.out.println("test2");
            strings.add("test2");
        });
        
        Thread.sleep(2 * SLEEP_DELAY);
        assertThat(strings.size(), is(2));
    }
    
    @Test
    public void testIsMainThread() {
        assertThat(mockScheduler.isMainThread(), is(false));
    }
    
    @Test
    public void testSchedule() throws InterruptedException {
        List<String> strings = new ArrayList<>();
        mockScheduler.scheduleTask(10, () -> {
            System.out.println("test1");
            strings.add("test1");
        });
        mockScheduler.scheduleTask(20, () -> {
            System.out.println("test2");
            strings.add("test2");
        });
        
        Thread.sleep(11 * SLEEP_DELAY);
        assertThat(strings.size(), is(1));
        Thread.sleep(11 * SLEEP_DELAY);
        assertThat(strings.size(), is(2));
    }
}
