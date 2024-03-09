/*
 * This file is part of lanterna (https://github.com/mabe02/lanterna).
 *
 * lanterna is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2010-2020 Martin Berglund
 */
package com.googlecode.lanterna.gui2;

import com.googlecode.lanterna.TerminalSize;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is a special label that contains not just a single text to display but a number of frames that are cycled
 * through. The class will manage a timer on its own and ensure the label is updated and redrawn. There is a static
 * helper method available to create the classic "spinning bar": {@code createClassicSpinningLine()}
 */
public class AnimatedLabel extends Label {
    private final List<String[]> frames;
    private volatile ScheduledThreadPoolExecutor threadPool = null;
    private volatile CountDownLatch taskLatch = null;
    private volatile TerminalSize combinedMaximumPreferredSize;
    private volatile int currentFrame;

    /**
     * Creates a new animated label, initially set to one frame. You will need to add more frames and call
     * {@code startAnimation()} for this to start moving.
     *
     * @param firstFrameText The content of the label at the first frame
     */
    public AnimatedLabel (String firstFrameText) {
        super(firstFrameText);
        frames = new ArrayList<>();
        currentFrame = 0;
        combinedMaximumPreferredSize = TerminalSize.ZERO;

        String[] lines = splitIntoMultipleLines(firstFrameText);
        frames.add(lines);
        ensurePreferredSize(lines);
    }

    /**
     * Creates a classic spinning bar which can be used to signal to the user that an operation in is process.
     *
     * @return {@code AnimatedLabel} instance which is setup to show a spinning bar
     */
    public static AnimatedLabel createClassicSpinningLine () {
        return createClassicSpinningLine(150);
    }

    /**
     * Creates a classic spinning bar which can be used to signal to the user that an operation in is process.
     *
     * @param millisecondsPerFrame The delay in between each frame in millis (first frame exclusive)
     *
     * @return {@code AnimatedLabel} instance which is setup to show a spinning bar
     */
    public static AnimatedLabel createClassicSpinningLine (long millisecondsPerFrame) {
        AnimatedLabel animatedLabel = new AnimatedLabel("-");
        animatedLabel.addFrame("\\");
        animatedLabel.addFrame("|");
        animatedLabel.addFrame("/");
        animatedLabel.startAnimation(millisecondsPerFrame);
        return animatedLabel;
    }

    /**
     * Adds one more frame at the end of the list of frames
     *
     * @param text Text to use for the label at this frame
     *
     * @return Itself
     */
    public synchronized AnimatedLabel addFrame (String text) {
        String[] lines = splitIntoMultipleLines(text);
        frames.add(lines);
        ensurePreferredSize(lines);
        return this;
    }

    private synchronized void ensurePreferredSize (String[] lines) {
        combinedMaximumPreferredSize = combinedMaximumPreferredSize.max(getBounds(lines, combinedMaximumPreferredSize));
    }

    /**
     * Starts the animation thread which will periodically call {@code nextFrame()} at the interval specified by the
     * {@code millisecondsPerFrame} parameter. After all frames have been cycled through, it will start over from the
     * first frame again.
     *
     * @param millisecondsPerFrame The delay in between each frame in millis (first frame exclusive)
     *
     * @return Itself
     */
    public synchronized AnimatedLabel startAnimation (long millisecondsPerFrame) {
        stopAnimation();
        taskLatch = new CountDownLatch(1);
        threadPool = new ScheduledThreadPoolExecutor(1, new AnimatedLabelThreadFactory());
        CompletableFuture.runAsync(() -> {

        })
        threadPool.scheduleAtFixedRate(this::animationTask, 0L, millisecondsPerFrame, TimeUnit.MILLISECONDS);
        return this;
    }

    protected void animationTask () {
        try {
            taskLatch.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (Thread.currentThread().isInterrupted()) {
            stopAnimation();
        } else {
            nextFrame();
        }
    }

    protected void monitorTask () {
        while (getTextGUI() == null) {
            if (Thread.currentThread().isInterrupted()) {
                stopAnimation();
            }
        }
        taskLatch.countDown();
        while (getTextGUI() != null) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
        }
        stopAnimation();
    }

    /**
     * Advances the animated label to the next frame. You normally don't need to call this manually as it will be done
     * by the animation thread.
     */
    public synchronized void nextFrame () {
        currentFrame++;
        if (currentFrame >= frames.size()) {
            currentFrame = 0;
        }
        super.setLines(frames.get(currentFrame));
        invalidate();
    }

    /**
     * Halts the animation thread and the label will stop at whatever was the current frame at the time when this was
     * called
     *
     * @return Itself
     */
    public synchronized AnimatedLabel stopAnimation () {
        if (threadPool != null) {
            threadPool.shutdownNow();
            threadPool = null;
        }
        return this;
    }

    @Override
    protected synchronized TerminalSize calculatePreferredSize () {
        return super.calculatePreferredSize().max(combinedMaximumPreferredSize);
    }

    public static final class AnimatedLabelThreadFactory implements ThreadFactory {
        public static final String NAME = AnimatedLabel.class.getSimpleName();
        private static final AtomicInteger POOL_NUMBER = new AtomicInteger(0);
        private final AtomicInteger threadNumber;
        private final String namePrefix;

        private AnimatedLabelThreadFactory () {
            namePrefix = String.format("%s-%d-thread-", NAME, POOL_NUMBER.getAndIncrement());
            threadNumber = new AtomicInteger(0);
        }

        @Override
        public Thread newThread (Runnable r) {
            final Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(false);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}
