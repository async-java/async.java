package org.ores.async;

/**
 * Token returned to a {@link NeoLock#acquire(Asyncc.IAsyncCallback) acquire} callback. The
 * holder calls {@link #releaseLock()} to relinquish the lock and let the next waiter (if any)
 * proceed. Unlike {@code synchronized} or {@link java.util.concurrent.locks.ReentrantLock},
 * the release can be invoked from any thread — async.java's lock is callback-based, not
 * thread-affine, which makes it suitable for callback-driven pipelines where the acquire and
 * release frequently happen on different worker threads.
 *
 * <p>Marked {@code public} from 0.2.2 onwards so consumers outside {@code org.ores.async}
 * can name the token type in their own lambdas / fields. Before 0.2.2 the class itself was
 * package-private, which effectively made {@code NeoLock} unusable from downstream code —
 * a long-latent API-accessibility bug surfaced by
 * <a href="https://github.com/ORESoftware/k8s-cluster/tree/dev/remote/spark-pipeline-server">dd-spark-pipeline-server</a>'s
 * composition-demo pipeline.
 */
public abstract class Unlock {

  boolean isImmediate = false;
  boolean callable = true;

  public Unlock(boolean isImmediate) {
    this.isImmediate = isImmediate;
  }

  public abstract void releaseLock();
}
