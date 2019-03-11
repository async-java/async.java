package org.ores.async;

import java.util.*;

import static org.ores.async.NeoWaterfallI.AsyncTask;
import static org.ores.async.NeoWaterfallI.AsyncCallback;

/**
 * See <a href="http://google.com">http://google.com</a>
 */
class NeoWaterfall {

  public static class AsyncTaskRunner<T, E> extends AsyncCallback<T, E> {

    private final WaterfallInternal w;

    private AsyncTaskRunner(WaterfallInternal w) {
      super(w.s, w.results);
      this.w = w;
    }

    @Override
    protected void doneInternal(NeoWaterfallI.Marker done, E e, Map.Entry<String, T> m) {

      synchronized (this.cbLock) {

        if (this.isFinished()) {
          new Error("Warning: Callback fired more than once.").printStackTrace(System.err);
          return;
        }

        this.setFinished(true);
        w.c.incrementFinished();

        if (w.s.isShortCircuited()) {
          return;
        }

      }

      if (m != null) {
        w.results.put(m.getKey(), m.getValue());
      }

      if (e != null) {
        w.s.setShortCircuited(true);
        NeoUtils.fireFinalCallback(w.s, e, w.results, w.f);
        return;
      }

      if (w.c.getFinishedCount() == w.tasks.size()) {
        NeoUtils.fireFinalCallback(w.s, null, w.results, w.f);
        return;
      }

      w.run();


    }
  }

  public static class WaterfallInternal<T, E> implements Runnable {
    final List<AsyncTask<T, E>> tasks;
    final HashMap<String, Object> results;
    final ShortCircuit s;
    final CounterLimit c;
    final Asyncc.IAsyncCallback<HashMap<String, Object>, E> f;

    public WaterfallInternal(
      final List<AsyncTask<T, E>> tasks,
      final HashMap<String, Object> results,
      final ShortCircuit s,
      final CounterLimit c,
      final Asyncc.IAsyncCallback<HashMap<String, Object>, E> f) {
      ////
      this.tasks = tasks;
      this.results = results;
      this.s = s;
      this.c = c;
      this.f = f;
    }

    @Override
    public void run() {

      final int startedCount = c.getStartedCount();

      if (startedCount >= tasks.size()) {
//      f.done(null, results);
        return;
      }

      final AsyncTask<T, E> t = tasks.get(startedCount);
      final var taskRunner = new AsyncTaskRunner<T, E>(this);
      c.incrementStarted();

      try {
        t.run(taskRunner);
      } catch (Exception e) {
        s.setShortCircuited(true);
        NeoUtils.fireFinalCallback(s, e, results, f);
      }

    }
  }


  @SuppressWarnings("Duplicates")
  static <T, E> void Waterfall2(
    final List<AsyncTask<T, E>> tasks,
    final Asyncc.IAsyncCallback<HashMap<String, Object>, E> f) {

    final HashMap<String, Object> results = new HashMap<>();

    if (tasks.size() < 1) {
      f.done(null, results);
      return;
    }

    final CounterLimit c = new CounterLimit(1);
    final ShortCircuit s = new ShortCircuit();

    new WaterfallInternal<>(tasks, results, s, c, f).run();
    NeoUtils.handleSameTickCall(s);

  }

  @SuppressWarnings("Duplicates")
  static <T, E> void Waterfall(
    final List<AsyncTask<T, E>> tasks,
    final Asyncc.IAsyncCallback<HashMap<String, Object>, E> f) {

    final HashMap<String, Object> results = new HashMap<>();

    if (tasks.size() < 1) {
      f.done(null, results);
      return;
    }

    final CounterLimit c = new CounterLimit(1);
    final ShortCircuit s = new ShortCircuit();

    WaterfallInternal(tasks, results, s, c, f);
    NeoUtils.handleSameTickCall(s);

  }

  @SuppressWarnings("Duplicates")
  private static <T, E> void WaterfallInternal(
    final List<AsyncTask<T, E>> tasks,
    final HashMap<String, Object> results,
    final ShortCircuit s,
    final CounterLimit c,
    final Asyncc.IAsyncCallback<HashMap<String, Object>, E> f) {

    final int startedCount = c.getStartedCount();

    if (startedCount >= tasks.size()) {
//      f.done(null, results);
      return;
    }

    final AsyncTask<T, E> t = tasks.get(startedCount);
    final var taskRunner = new AsyncCallback<T, E>(s, results) {

      protected void doneInternal(final NeoWaterfallI.Marker done, final E e, final Map.Entry<String, T> m) {

        synchronized (this.cbLock) {

          if (this.isFinished()) {
            new Error("Warning: Callback fired more than once.").printStackTrace(System.err);
            return;
          }

          this.setFinished(true);
          c.incrementFinished();

          if (s.isShortCircuited()) {
            return;
          }

        }

        if (m != null) {
          results.put(m.getKey(), m.getValue());
        }

        if (e != null) {
          s.setShortCircuited(true);
          NeoUtils.fireFinalCallback(s, e, results, f);
          return;
        }

        if (c.getFinishedCount() == tasks.size()) {
          NeoUtils.fireFinalCallback(s, null, results, f);
          return;
        }

        WaterfallInternal(tasks, results, s, c, f);

      }

    };

    c.incrementStarted();

    try {
      t.run(taskRunner);
    } catch (Exception e) {
      s.setShortCircuited(true);
      NeoUtils.fireFinalCallback(s, e, results, f);
    }

  }
}
