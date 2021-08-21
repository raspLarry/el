package io.el.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
public class SingleThreadEventLoopTest {
  @Nested
  @DisplayName("On takeTask() method")
  class TakeTaskMethod {
    @Test
    @DisplayName("When eventloop is given tasks, then execute them")
    public void runTasks() {
      final SingleThreadEventLoop eventLoop = new SingleThreadEventLoop(
          new ThreadPerTaskExecutor(Executors.defaultThreadFactory())
      ) {
        @Override
        protected void run() {
          while (!confirmShutdown()) {
            Runnable task = takeTask();
            if (task != null) {
              task.run();
            }
          }
        }
      };

      CountDownLatch latch = new CountDownLatch(3);

      assertTimeout(Duration.ofSeconds(1), () -> {
        TestRunnable task1 = new TestRunnable(latch);
        eventLoop.execute(task1);

        TestRunnable task2 = new TestRunnable(latch);
        eventLoop.schedule(task2, 200, TimeUnit.MILLISECONDS);

        TestRunnable task3 = new TestRunnable(latch);
        eventLoop.execute(task3);

        latch.await();

        assertEquals(task1.order, 1);
        assertEquals(task2.order, 3);
        assertEquals(task3.order, 2);
      });
    }

    @Test
    @DisplayName("When eventloop is given multiple scheduled tasks, then handle them in proper order")
    public void runScheduledTasks() {
      final SingleThreadEventLoop eventLoop = new SingleThreadEventLoop(
          new ThreadPerTaskExecutor(Executors.defaultThreadFactory())
      ) {
        @Override
        protected void run() {
          while (!confirmShutdown()) {
            Runnable task = takeTask();
            if (task != null) {
              task.run();
            }
          }
        }
      };

      CountDownLatch latch = new CountDownLatch(3);

      assertTimeout(Duration.ofSeconds(1), () -> {
        TestRunnable task1 = new TestRunnable(latch);
        eventLoop.schedule(task1, 600, TimeUnit.MILLISECONDS);

        TestRunnable task2 = new TestRunnable(latch);
        eventLoop.schedule(task2, 200, TimeUnit.MILLISECONDS);

        TestRunnable task3 = new TestRunnable(latch);
        eventLoop.schedule(task3, 400, TimeUnit.MILLISECONDS);

        latch.await();

        assertEquals(task1.order, 3);
        assertEquals(task2.order, 1);
        assertEquals(task3.order, 2);
      });
    }
  }

  private static final class TestRunnable implements Runnable {
    static AtomicInteger ORDER = new AtomicInteger(0);

    final CountDownLatch latch;
    int order;

    TestRunnable(CountDownLatch latch) {
      this.latch = latch;
    }

    @Override
    public void run() {
      order = ORDER.incrementAndGet();
      latch.countDown();
    }
  }
}
