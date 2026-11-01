/*
 * Copyright © 2026 Frank Hossfeld
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nalukit.paniolo.model.util;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Measures sequential tasks with the monotonic clock {@link System#nanoTime()}.
 * Constructors create a stopped watch; {@link #start(String)} creates a running watch. Finish
 * the current task with {@link #stop()} before starting another with {@link #stopOver(String)}.
 * Totals include completed tasks only, not the elapsed time of a running task.
 *
 * <p>Instances are mutable and not thread-safe. Keep one watch per operation. Task history is
 * retained by default and can be disabled with {@link #setKeepTaskList(boolean)}; the last task
 * and cumulative totals remain available. Millisecond values are truncated to whole milliseconds.
 *
 * <pre>{@code
 * PanioloStopWatch watch = PanioloStopWatch.start("read invoice");
 * // perform the operation
 * watch.stop();
 * long elapsedNanos = watch.getLastTaskTimeNanos();
 * }</pre>
 */
public class PanioloStopWatch {
  private final String         id;
  private final List<TaskInfo> taskList;
  private       boolean        keepTaskList;
  private       long           startTimeNanos;
  private       String         currentTaskName;
  private       TaskInfo       lastTaskInfo;
  private       int            taskCount;
  private       long           totalTimeNanos;

  /**
   * Creates a stopped watch with an empty identifier.
   */
  public PanioloStopWatch() {
    this("");
  }

  /**
   * Creates a stopped watch with the supplied identifier.
   *
   * @param id identifier used in summaries and log messages
   */
  public PanioloStopWatch(String id) {
    this.keepTaskList = true;
    this.taskList     = new ArrayList<>(1);
    this.id           = id;
  }

  private static long nanosToMillis(long duration) {
    return TimeUnit.NANOSECONDS.toMillis(duration);
  }

  private static double nanosToSeconds(long duration) {
    return (double) duration / 1.0E9;
  }

  /**
   * Creates a watch and immediately starts an unnamed task.
   *
   * @return a new running watch
   */
  public static PanioloStopWatch start()
      throws IllegalStateException {
    PanioloStopWatch stopWatch = new PanioloStopWatch();
    stopWatch.currentTaskName = "";
    stopWatch.startTimeNanos  = System.nanoTime();
    return stopWatch;
  }

  /**
   * Creates a watch whose identifier and first task name are the supplied name.
   *
   * @param taskName name of the first task and identifier of the watch
   * @return a new running watch
   */
  public static PanioloStopWatch start(String taskName)
      throws IllegalStateException {
    PanioloStopWatch stopWatch = new PanioloStopWatch(taskName);
    stopWatch.currentTaskName = taskName;
    stopWatch.startTimeNanos  = System.nanoTime();
    return stopWatch;
  }

  /**
   * Returns the identifier supplied at construction.
   *
   * @return the watch identifier
   */
  public String getId() {
    return this.id;
  }

  /**
   * Controls whether subsequently completed tasks are retained in the history.
   * Existing history is not cleared; totals and the last completed task remain available.
   *
   * @param keepTaskList true to retain task history, false to stop adding entries
   */
  public void setKeepTaskList(boolean keepTaskList) {
    this.keepTaskList = keepTaskList;
  }

  /**
   * Starts another task on a stopped watch without clearing completed task totals.
   *
   * @param taskName name of the new task; must not be null
   * @throws IllegalStateException if a task is already running
   */
  public void stopOver(String taskName)
      throws IllegalStateException {
    if (this.currentTaskName != null) {
      throw new IllegalStateException("Can't start EisStopWatch: it's already running");
    } else {
      this.currentTaskName = taskName;
      this.startTimeNanos  = System.nanoTime();
    }
  }

  /**
   * Completes the running task and adds its duration to the total.
   *
   * @throws IllegalStateException if no task is running
   */
  public void stop()
      throws IllegalStateException {
    if (this.currentTaskName == null) {
      throw new IllegalStateException("Can't stop EisStopWatch: it's not running");
    } else {
      long lastTime = System.nanoTime() - this.startTimeNanos;
      this.totalTimeNanos += lastTime;
      this.lastTaskInfo = new TaskInfo(this.currentTaskName,
                                       lastTime);
      if (this.keepTaskList) {
        this.taskList.add(this.lastTaskInfo);
      }

      ++this.taskCount;
      this.currentTaskName = null;
    }
  }

  /**
   * Reports whether a task is currently running.
   *
   * @return true between starting and stopping a task
   */
  public boolean isRunning() {
    return this.currentTaskName != null;
  }

  /**
   * Returns the name of the running task.
   *
   * @return the current name, or null when stopped
   */
  public String currentTaskName() {
    return this.currentTaskName;
  }

  /**
   * Returns the duration of the last completed task.
   *
   * @return elapsed nanoseconds
   * @throws IllegalStateException if no task has completed
   */
  public long getLastTaskTimeNanos()
      throws IllegalStateException {
    if (this.lastTaskInfo == null) {
      throw new IllegalStateException("No tasks run: can't get last task interval");
    } else {
      return this.lastTaskInfo.getTimeNanos();
    }
  }

  /**
   * Returns the duration of the last completed task in whole milliseconds.
   *
   * @return elapsed milliseconds, truncated
   * @throws IllegalStateException if no task has completed
   */
  public long getLastTaskTimeMillis()
      throws IllegalStateException {
    if (this.lastTaskInfo == null) {
      throw new IllegalStateException("No tasks run: can't get last task interval");
    } else {
      return this.lastTaskInfo.getTimeMillis();
    }
  }

  /**
   * Returns the name of the last completed task.
   *
   * @return the completed task name
   * @throws IllegalStateException if no task has completed
   */
  public String getLastTaskName()
      throws IllegalStateException {
    if (this.lastTaskInfo == null) {
      throw new IllegalStateException("No tasks run: can't get last task name");
    } else {
      return this.lastTaskInfo.getTaskName();
    }
  }

  /**
   * Returns the immutable measurement of the last completed task.
   *
   * @return the last task, even when task history is disabled
   * @throws IllegalStateException if no task has completed
   */
  public TaskInfo getLastTaskInfo()
      throws IllegalStateException {
    if (this.lastTaskInfo == null) {
      throw new IllegalStateException("No tasks run: can't get last task info");
    } else {
      return this.lastTaskInfo;
    }
  }

  /**
   * Returns the sum of completed task durations.
   *
   * @return total elapsed nanoseconds, excluding a running task
   */
  public long getTotalTimeNanos() {
    return this.totalTimeNanos;
  }

  /**
   * Returns the sum of completed task durations in whole milliseconds.
   *
   * @return total elapsed milliseconds, truncated
   */
  public long getTotalTimeMillis() {
    return nanosToMillis(this.totalTimeNanos);
  }

  /**
   * Returns the sum of completed task durations in seconds.
   *
   * @return total elapsed seconds, including fractional seconds
   */
  public double getTotalTimeSeconds() {
    return nanosToSeconds(this.totalTimeNanos);
  }

  /**
   * Returns the number of completed tasks, including tasks not retained in history.
   *
   * @return completed task count
   */
  public int getTaskCount() {
    return this.taskCount;
  }

  /**
   * Returns a new array of the retained task measurements in completion order.
   *
   * @return retained task history; may omit tasks completed while history was disabled
   * @throws UnsupportedOperationException if task history is currently disabled
   */
  public TaskInfo[] getTaskInfo() {
    if (!this.keepTaskList) {
      throw new UnsupportedOperationException("Task info is not being kept!");
    } else {
      return this.taskList.toArray(new TaskInfo[0]);
    }
  }

  /**
   * Formats the identifier and total elapsed nanoseconds.
   *
   * @return a single-line summary
   */
  public String shortSummary() {
    String var10000 = this.getId();
    return "EisStopWatch '" + var10000 + "': running time = " + this.getTotalTimeNanos() + " ns";
  }

  /**
   * Formats a table of retained task durations and their shares of total time.
   * Number formatting uses the JVM default locale.
   *
   * @return a multiline report, or a note when task history is disabled
   */
  public String prettyPrint() {
    StringBuilder sb = new StringBuilder(this.shortSummary());
    sb.append('\n');
    if (!this.keepTaskList) {
      sb.append("No task info kept");
    } else {
      sb.append("---------------------------------------------\n");
      sb.append("ns         %     Task name\n");
      sb.append("---------------------------------------------\n");
      NumberFormat nf = NumberFormat.getNumberInstance();
      nf.setMinimumIntegerDigits(9);
      nf.setGroupingUsed(false);
      NumberFormat pf = NumberFormat.getPercentInstance();
      pf.setMinimumIntegerDigits(3);
      pf.setGroupingUsed(false);
      Arrays.stream(this.getTaskInfo(),
                    0,
                    this.getTaskInfo().length)
            .forEach(task -> {
              sb.append(nf.format(task.getTimeNanos()))
                .append("  ");
              sb.append(pf.format((double) task.getTimeNanos() / (double) this.getTotalTimeNanos()))
                .append("  ");
              sb.append(task.getTaskName())
                .append('\n');
            });
    }

    return sb.toString();
  }

  /**
   * Formats a summary and the retained tasks with their durations.
   *
   * @return a diagnostic summary
   */
  public String toString() {
    StringBuilder sb = new StringBuilder(this.shortSummary());
    if (this.keepTaskList) {
      for (TaskInfo task : this.getTaskInfo()) {
        sb.append("; [")
          .append(task.getTaskName())
          .append("] took ")
          .append(task.getTimeNanos())
          .append(" ns");
        long percent = Math.round(100.0 * (double) task.getTimeNanos() / (double) this.getTotalTimeNanos());
        sb.append(" = ")
          .append(percent)
          .append('%');
      }
    } else {
      sb.append("; no task info kept");
    }

    return sb.toString();
  }

  /**
   * Immutable name and elapsed duration of one completed task. Created by {@link #stop()} and
   * safe to retain or share independently of the mutable stopwatch.
   */
  public static final class TaskInfo {
    private final String taskName;
    private final long   timeNanos;

    TaskInfo(String taskName,
             long timeNanos) {
      this.taskName  = taskName;
      this.timeNanos = timeNanos;
    }

    /**
     * Returns the name of this completed task.
     *
     * @return the task name
     */
    public String getTaskName() {
      return this.taskName;
    }

    /**
     * Returns the measured duration.
     *
     * @return elapsed nanoseconds
     */
    public long getTimeNanos() {
      return this.timeNanos;
    }

    /**
     * Returns the measured duration in whole milliseconds.
     *
     * @return elapsed milliseconds, truncated
     */
    public long getTimeMillis() {
      return PanioloStopWatch.nanosToMillis(this.timeNanos);
    }

    /**
     * Returns the measured duration in seconds.
     *
     * @return elapsed seconds, including fractional seconds
     */
    public double getTimeSeconds() {
      return PanioloStopWatch.nanosToSeconds(this.timeNanos);
    }
  }
}
