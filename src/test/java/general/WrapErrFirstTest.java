package general;

import org.junit.Test;
import org.ores.async.Asyncc;
import org.ores.async.WrapErrFirst;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.ores.async.WrapErrFirst.wrap;

/**
 * Pins the {@link WrapErrFirst} contract:
 * <ul>
 *   <li>single-arg form invokes the success consumer when {@code err == null};</li>
 *   <li>single-arg form throws when {@code err} is non-null, wrapping a {@link Throwable} cause
 *       if available;</li>
 *   <li>two-arg form routes through the appropriate branch and never throws on its own;</li>
 *   <li>integrates with {@link Asyncc#Parallel} end-to-end (canary that the wrap return type is
 *       assignable to the combinator's final-callback parameter).</li>
 * </ul>
 */
public class WrapErrFirstTest {

  @Test
  public void wrapSingleArg_fires_onSuccess_when_no_error() {
    final AtomicReference<String> got = new AtomicReference<>();
    final Asyncc.IAsyncCallback<String, Object> c = wrap(got::set);

    c.done(null, "hello");

    assertEquals("hello", got.get());
  }

  @Test
  public void wrapSingleArg_throws_when_error_is_Throwable() {
    final IllegalStateException cause = new IllegalStateException("boom");
    final Asyncc.IAsyncCallback<String, Object> c = wrap(v -> fail("should not call onSuccess"));

    try {
      c.done(cause, null);
      fail("expected RuntimeException");
    } catch (RuntimeException re) {
      assertTrue(
          "message should mention 'unhandled error'",
          re.getMessage().contains("unhandled error"));
      assertSame("Throwable err should be wrapped as cause", cause, re.getCause());
    }
  }

  @Test
  public void wrapSingleArg_throws_when_error_is_non_Throwable_object() {
    final Asyncc.IAsyncCallback<String, Object> c = wrap(v -> fail("should not call onSuccess"));

    try {
      c.done("string-as-error", null);
      fail("expected RuntimeException");
    } catch (RuntimeException re) {
      assertTrue(
          "message should include error.toString()",
          re.getMessage().contains("string-as-error"));
      assertNull("non-Throwable err has no cause", re.getCause());
    }
  }

  @Test
  public void wrapTwoArg_routes_to_onSuccess_when_no_error() {
    final AtomicReference<String> success = new AtomicReference<>();
    final AtomicReference<Object> err = new AtomicReference<>();

    wrap((String v) -> success.set(v), err::set).done(null, "ok");

    assertEquals("ok", success.get());
    assertNull(err.get());
  }

  @Test
  public void wrapTwoArg_routes_to_onError_when_error_present() {
    final AtomicReference<String> success = new AtomicReference<>();
    final AtomicReference<Object> err = new AtomicReference<>();

    final IllegalStateException cause = new IllegalStateException("nope");
    wrap((String v) -> success.set(v), err::set).done(cause, null);

    assertNull(success.get());
    assertSame(cause, err.get());
  }

  @Test
  public void wrapTwoArg_never_throws_on_its_own() {
    // If onError throws, the throw comes from the user's consumer, not from wrap itself.
    // That's exercised implicitly by the rest of the suite; this test just confirms the
    // null/empty path never throws under either branch.
    wrap((String v) -> {}, e -> {}).done(null, "value");
    wrap((String v) -> {}, e -> {}).done(new RuntimeException("x"), null);
  }

  @Test
  public void wrap_integrates_with_Asyncc_Parallel_endtoend() {
    // Canary: does Parallel accept the type wrap() returns as its final callback?
    final AtomicReference<List<String>> seen = new AtomicReference<>();

    final List<Asyncc.AsyncTask<String, Throwable>> tasks = List.of(
        c -> c.success("alpha"),
        c -> c.success("beta")
    );

    Asyncc.Parallel(tasks, wrap(seen::set));

    assertNotNull(seen.get());
    assertEquals(List.of("alpha", "beta"), seen.get());
  }

  @Test
  public void c_success_helper_works_alongside_c_done() {
    // success(v) / fail(e) on IAsyncCallback are equivalent to done(null, v) / done(e, null).
    final AtomicReference<String> got = new AtomicReference<>();
    final AtomicReference<Object> err = new AtomicReference<>();

    Asyncc.IAsyncCallback<String, Object> sink = (e, v) -> {
      err.set(e);
      got.set(v);
    };

    sink.success("yay");
    assertEquals("yay", got.get());
    assertNull(err.get());

    sink.fail("oh no");
    assertEquals("oh no", err.get());
    assertNull(got.get());
  }
}
