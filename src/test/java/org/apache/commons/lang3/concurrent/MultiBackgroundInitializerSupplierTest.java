/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.lang3.concurrent;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Field;

import org.apache.commons.lang3.function.FailableConsumer;
import org.apache.commons.lang3.function.FailableSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for {@link MultiBackgroundInitializer}.
 */
public class MultiBackgroundInitializerSupplierTest extends MultiBackgroundInitializerTest {

    private NullPointerException npe = null;
    private IOException ioException = null;
    private FailableConsumer<?, ?> ioExceptionConsumer = null;
    private FailableConsumer<?, ?> nullPointerExceptionConsumer = null;

    @BeforeEach
    public void setUpException() throws Exception {
        npe = new NullPointerException();
        ioException = new IOException();
        ioExceptionConsumer = (CloseableCounter cc) -> {
            throw ioException;
        };
        nullPointerExceptionConsumer = (CloseableCounter cc) -> {
            throw npe;
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected AbstractChildBackgroundInitializer createChildBackgroundInitializer() {
        return new SupplierChildBackgroundInitializer();
    }

    /**
     * Tests that close() method closes the wrapped object
     *
     * @throws Exception
     */
    @Test
    public void testClose()
            throws ConcurrentException, InterruptedException {
        final AbstractChildBackgroundInitializer childOne = createChildBackgroundInitializer();
        final AbstractChildBackgroundInitializer childTwo = createChildBackgroundInitializer();

        assertFalse(initializer.isInitialized(), "Initalized without having anything to initalize");

        initializer.addInitializer("child one", childOne);
        initializer.addInitializer("child two", childTwo);

        assertFalse(childOne.getCloseableCounter().isClosed(), "child one closed() succeeded before start()");
        assertFalse(childTwo.getCloseableCounter().isClosed(), "child two closed() succeeded before start()");

        initializer.start();

        long startTime = System.currentTimeMillis();
        long waitTime = 3000;
        long endTime = startTime + waitTime;
        //wait for the children to start
        while (! childOne.isStarted() || ! childTwo.isStarted()) {
            if (System.currentTimeMillis() > endTime) {
                fail("children never started");
                Thread.sleep(PERIOD_MILLIS);
            }
        }

        assertFalse(childOne.getCloseableCounter().isClosed(), "child one close() succeeded after start() but before close()");
        assertFalse(childTwo.getCloseableCounter().isClosed(), "child two close() succeeded after start() but before close()");

        childOne.get(); // ensure this child finishes initialising
        childTwo.get(); // ensure this child finishes initialising

        assertFalse(childOne.getCloseableCounter().isClosed(), "child one initialising succeeded after start() but before close()");
        assertFalse(childTwo.getCloseableCounter().isClosed(), "child two initialising succeeded after start() but before close()");

        try {
            initializer.close();
        } catch (Exception e) {
            fail();
        }

        assertTrue(childOne.getCloseableCounter().isClosed(), "child one close() did not succeed");
        assertTrue(childOne.getCloseableCounter().isClosed(), "child two close() did not succeed");
    }

    /**
     * Tests that close() wraps a checked exception from a child initializer in an IllegalStateException as the first suppressed under in an IllegalStateException
     *
     * @throws Exception
     */
    @Test
    public void testCloseWithCheckedException() throws Exception {
        final AbstractChildBackgroundInitializer childOne = new SupplierChildBackgroundInitializer(ioExceptionConsumer);

        initializer.addInitializer("child one", childOne);
        initializer.start();

        long startTime = System.currentTimeMillis();
        long waitTime = 3000;
        long endTime = startTime + waitTime;
        //wait for the children to start
        while (! childOne.isStarted()) {
            if (System.currentTimeMillis() > endTime) {
                fail("children never started");
                Thread.sleep(PERIOD_MILLIS);
            }
        }

        childOne.get(); // ensure the Future has completed.
        try {
            initializer.close();
            fail();
        } catch (Exception e) {
            assertThat(e, instanceOf(IllegalStateException.class));
            assertSame(ioException, e.getSuppressed()[0].getCause());
        }
    }

    /**
     * Tests that close() wraps a runtime exception from a child initializer as the first suppressed under in an IllegalStateException
     *
     * @throws Exception
     */
    @Test
    public void testCloseWithRuntimeException() throws Exception {
        final AbstractChildBackgroundInitializer childOne = new SupplierChildBackgroundInitializer(nullPointerExceptionConsumer);

        initializer.addInitializer("child one", childOne);
        initializer.start();

        long startTime = System.currentTimeMillis();
        long waitTime = 3000;
        long endTime = startTime + waitTime;
        //wait for the children to start
        while (! childOne.isStarted()) {
            if (System.currentTimeMillis() > endTime) {
                fail("children never started");
                Thread.sleep(PERIOD_MILLIS);
            }
        }

        childOne.get(); // ensure the Future has completed.
        try {
            initializer.close();
            fail();
        } catch (Exception e) {
            assertThat(e, instanceOf(IllegalStateException.class));
            assertSame(npe, e.getSuppressed()[0]);
        }
    }

    /**
     * Tests that calling close() on a MultiBackgroundInitializer with two children that both throw exceptions throws
     * an IllegalStateException and both the child exceptions are present
     *
     * @throws Exception
     */
    @Test
    public void testCloseWithTwoExceptions()
            throws ConcurrentException, InterruptedException {

        final AbstractChildBackgroundInitializer childOne = new SupplierChildBackgroundInitializer(ioExceptionConsumer);
        final AbstractChildBackgroundInitializer childTwo = new SupplierChildBackgroundInitializer(nullPointerExceptionConsumer);

        initializer.addInitializer("child one", childOne);
        initializer.addInitializer("child two", childTwo);

        initializer.start();

        long startTime = System.currentTimeMillis();
        long waitTime = 3000;
        long endTime = startTime + waitTime;
        //wait for the children to start
        while (! childOne.isStarted() || ! childTwo.isStarted()) {
            if (System.currentTimeMillis() > endTime) {
                fail("children never started");
                Thread.sleep(PERIOD_MILLIS);
            }
        }

        childOne.get(); // ensure this child finishes initialising
        childTwo.get(); // ensure this child finishes initialising

        try {
            initializer.close();
            fail();
        } catch (Exception e) {
            // We don't actually know which order the children will be closed in
            boolean foundChildOneException = false;
            boolean foundChildTwoException = false;

            for (Throwable t : e.getSuppressed()) {
                if (t.getCause() != null && t.getCause().equals(ioException)) {
                    foundChildOneException = true;
                }
                if (t.equals(npe)) {
                    foundChildTwoException = true;
                }
            }

            assertTrue(foundChildOneException);
            assertTrue(foundChildTwoException);
        }
    }

    /**
     * A concrete implementation of {@code BackgroundInitializer} used for
     * defining background tasks for {@code MultiBackgroundInitializer}.
     */
    private static final class SupplierChildBackgroundInitializer extends AbstractChildBackgroundInitializer {

        SupplierChildBackgroundInitializer() {
            this((CloseableCounter cc) -> cc.close());
        }

        SupplierChildBackgroundInitializer(FailableConsumer<?, ?> consumer) {
            try {
                // Use reflection here because the constructors we need are private
                FailableSupplier<?, ?> supplier = () -> initializeInternal();
                Field initializer = AbstractConcurrentInitializer.class.getDeclaredField("initializer");
                initializer.setAccessible(true);
                initializer.set(this, supplier);

                Field closer = AbstractConcurrentInitializer.class.getDeclaredField("closer");
                closer.setAccessible(true);
                closer.set(this, consumer);
            } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
                fail();
            }
        }
    }
}
