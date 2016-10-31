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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * Created by Martin on 31.10.2016.
 */
public class CurrentChainTest {
    
    private TaskChainFactory factory;
    private MockScheduler mockScheduler;
    
    @Before
    public void before() {
        mockScheduler = new MockScheduler();
        factory = MockTaskChainFactory.create(mockScheduler);
    }
    
    @After
    public void after() {
        mockScheduler.shutdown();
    }
    
    @Test
    public void testCurrent() {
        List<String> strings = Collections.synchronizedList(new ArrayList<>());
        factory.newChain().
                current(() -> strings.add("test1")).
                execute();
        assertThat(strings.size(), is(1));
    }
    
    @Test
    public void testCurrentFirst() {
        List<String> strings = new ArrayList<>();
        factory.newChain().
                currentFirst(() -> "test1").
                current(strings::add).
                execute();
        assertThat(strings.size(), is(1));
        assertThat(strings.get(0), is("test1"));
    }
    
    @Test
    public void testCurrentLast() {
        List<String> strings = new ArrayList<>();
        factory.newChain().
                currentFirst(() -> "test1").
                current((s) -> s).
                currentLast(strings::add).
                execute();
        assertThat(strings.size(), is(1));
        assertThat(strings.get(0), is("test1"));
    }
    
    @Test
    public void testCurrentWithException() {
        AtomicBoolean ran = new AtomicBoolean(false);
        factory.newChain().current(() -> {
            throw new RuntimeException("expected");
        }).execute(() -> {
        }, (e, t) -> {
            ran.set(true);
            
            assertThat(e.getMessage(), is("expected"));
        });
        
        assertThat(ran.get(), is(true));
    }
    
    @Test
    public void testCurrentWithAbort() {
        AtomicBoolean ran = new AtomicBoolean(false);
        factory.newChain().current(TaskChain::abort).current(() -> ran.set(true)).execute();
        
        assertThat(ran.get(), is(false));
    }
    
    @Test
    public void testCurrentWithAbortIf() {
        AtomicBoolean ran = new AtomicBoolean(false);
        // should abort
        ran.set(false);
        factory.newChain().currentFirst(() -> true).abortIf(true).current(() -> ran.set(true)).execute();
        assertThat(ran.get(), is(false));
        // should not abort
        ran.set(false);
        factory.newChain().currentFirst(() -> false).abortIf(true).current(() -> ran.set(true)).execute();
        assertThat(ran.get(), is(true));
        // should abort
        ran.set(false);
        factory.newChain().currentFirst(() -> "ABORT").abortIf("ABORT").current(() -> ran.set(true)).execute();
        assertThat(ran.get(), is(false));
        // should not abort
        ran.set(false);
        factory.newChain().currentFirst(() -> "ABORT").abortIf("DONT").current(() -> ran.set(true)).execute();
        assertThat(ran.get(), is(true));
        // should abort
        ran.set(false);
        factory.newChain().currentFirst(() -> new Integer(42)).abortIf(new Integer(42)).current(() -> ran.set(true)).execute();
        assertThat(ran.get(), is(false));
        // should not abort
        ran.set(false);
        factory.newChain().currentFirst(() -> new Integer(42)).abortIf(new Integer(1337)).current(() -> ran.set(true)).execute();
        assertThat(ran.get(), is(true));
    }
    
    @Test
    public void testCurrentWithAbortIfNot() {
        AtomicBoolean ran = new AtomicBoolean(false);
        // should not abort
        ran.set(false);
        factory.newChain().currentFirst(() -> true).abortIfNot(true).current(() -> ran.set(true)).execute();
        assertThat(ran.get(), is(true));
        // should abort
        ran.set(false);
        factory.newChain().currentFirst(() -> false).abortIfNot(true).current(() -> ran.set(true)).execute();
        assertThat(ran.get(), is(false));
        // should not abort
        ran.set(false);
        factory.newChain().currentFirst(() -> "ABORT").abortIfNot("ABORT").current(() -> ran.set(true)).execute();
        assertThat(ran.get(), is(true));
        // should abort
        ran.set(false);
        factory.newChain().currentFirst(() -> "ABORT").abortIfNot("DONT").current(() -> ran.set(true)).execute();
        assertThat(ran.get(), is(false));
        // should not abort
        ran.set(false);
        factory.newChain().currentFirst(() -> new Integer(42)).abortIfNot(new Integer(42)).current(() -> ran.set(true)).execute();
        assertThat(ran.get(), is(true));
        // should abort
        ran.set(false);
        factory.newChain().currentFirst(() -> new Integer(42)).abortIfNot(new Integer(1337)).current(() -> ran.set(true)).execute();
        assertThat(ran.get(), is(false));
    }
}
