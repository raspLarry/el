package io.el.concurrent;

import static io.el.internal.ObjectUtil.checkPositive;

import io.el.concurrent.EventLoopChooserFactory.EventLoopChooser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public abstract class DefaultEventLoopGroup implements EventLoopGroup {

  static final long DEFAULT_SHUTDOWN_TIMEOUT = 15;
  private final List<EventLoop> children;
  private final EventLoopChooser chooser;

  protected DefaultEventLoopGroup(
      int nThreads,
      Executor executor,
      EventLoopChooserFactory chooserFactory
  ) {
    checkPositive(nThreads, "nThreads");

    if (executor == null) {
      executor = new ThreadPerTaskExecutor(this.newDefaultThreadFactory());
    }

    this.children = new ArrayList<>();
    for (int i = 0; i < nThreads; i++) {
      try {
        this.children.add(this.newChild(executor));
      } catch (Exception e) {
        for (int j = 0; j < i; j++) {
          if (this.children.get(j).shutdownGracefully(DEFAULT_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)) {
            EventLoop el = this.children.get(j);
            try {
              while (!el.isTerminated()) {
                el.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
              }
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
              break;
            }
          }
        }
        throw new IllegalStateException("failed to create a child event loop", e);
      }
    }

    this.chooser = chooserFactory.newChooser(this.children);
  }

  protected abstract EventLoop newChild(Executor executor) throws Exception;

  protected abstract ThreadFactory newDefaultThreadFactory();

  @Override
  public EventLoop next() {
    return this.chooser.next();
  }

  @Override
  public Iterator<EventLoop> iterator() {
    return this.children.iterator();
  }

  @Override
  public void shutdown() {
    this.children.forEach(EventLoop::shutdown);
  }

  @Override
  public List<Runnable> shutdownNow() {
    return this.children.stream()
        .flatMap(c -> c.shutdownNow().stream())
        .collect(Collectors.toList());
  }

  @Override
  public boolean isShutdown() {
    return this.children.stream()
        .map(c -> !c.isShutdown())
        .findFirst()
        .orElse(true);
  }

  @Override
  public boolean isTerminated() {
    return this.children.stream()
        .map(c -> !c.isTerminated())
        .findFirst()
        .orElse(true);
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    long deadline = System.nanoTime() + unit.toNanos(timeout);
    for (EventLoop el : this.children) {
      long timeLeft = deadline - System.nanoTime();
      while (timeLeft > 0 && !el.awaitTermination(timeLeft, TimeUnit.NANOSECONDS)) {
        timeLeft = deadline - System.nanoTime();
      }
      if (timeLeft <= 0) {
        break;
      }
    }
    return this.isTerminated();
  }

  @Override
  public <V> Promise<V> submit(Callable<V> task) {
    return this.next().submit(task);
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return this.next().submit(task, result);
  }

  @Override
  public Promise<?> submit(Runnable task) {
    return this.next().submit(task);
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    return this.next().invokeAll(tasks);
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout,
      TimeUnit unit) throws InterruptedException {
    return this.next().invokeAll(tasks, timeout, unit);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    return this.next().invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return this.next().invokeAny(tasks, timeout, unit);
  }

  @Override
  public void execute(Runnable command) {
    this.next().execute(command);
  }
}
