package org.ores;

import java.util.*;
import static java.util.Arrays.asList;

class ShortCircuit {
	
	private boolean isShortCircuited = false;
	
	public boolean isShortCircuited(){
		return this.isShortCircuited;
	}
	
	public boolean setShortCircuited(boolean v){
		return this.isShortCircuited = v;
	}
	
}


class Limit {
	private int val;
	private int current = 0;
	
	 Limit(int val) {
		this.val = val;
	}
	
	public Limit() {
		this.val = 1;
	}
	
	public int getVal() {
		return this.val;
	}
	
	public int getCurrent() {
		return this.current;
	}
	
	 void increment() {
		this.current++;
	}
	
	 void decrement() {
		this.current--;
	}
	
	 boolean isBelowCapacity() {
		return this.current < this.val;
	}
	
}

class Counter {
	
	private int started = 0;
	private int finished = 0;
	
	void incrementStarted() {
		this.started++;
	}
	
	void incrementFinished() {
		this.finished++;
	}
	
	int getStartedCount() {
		return this.started;
	}
	
	int getFinishedCount() {
		return this.finished;
	}
}



public class Asyncc {
	
	public static class KeyValue<V> {
		
		public String key;
		public V value;
		
		public KeyValue(String key, V value) {
			this.key = key;
			this.value = value;
		}
	}
	
	public static interface Mapper<V, T, E> {
		public void map(KeyValue<V> v, AsyncCallback<T, E> cb);
	}
	
	public static interface IAsyncCallback<T, E> {
		public void done(E e, T v);
	}
	
	public static abstract class AsyncCallback<T, E> implements IAsyncCallback<T, E> {
		private ShortCircuit s;
		
		public AsyncCallback(ShortCircuit s){
			this.s = s;
		}
		
		public boolean isShortCircuited(){
			return this.s.isShortCircuited();
		}
		
	}
	
	public static interface AsyncTask<T, E> {
		public void run(AsyncCallback<T, E> cb);
//    public void run(AsyncCallback<T, E> cb, Integer v);
	}


//  public static interface FinalCallback {
//    public void run(Object e, List<Object> v);
//  }
	
	public static AsyncTask zoom() {
		return v -> {
			
			v.done(null, null);
		};
	}
	
	public static void main() {
		Asyncc.Series(asList(
			Asyncc.zoom(),
			Asyncc.Parallel(asList(
				z -> {
					z.done(null, null);
				},
				Asyncc.zoom()
				)
			)),
			(e, results) -> {
			
			});
		
		Asyncc.Parallel(asList(
			
			v -> {
				v.done(null, null);
			},
			
			Asyncc.zoom()
			
			),
			(e, results) -> {
			
			});
	}
	
	public static <T, E> AsyncTask<List<T>, E> Parallel(AsyncTask<T, E>... tasks) {
		return cb -> Asyncc.<T, E>Parallel(List.of(tasks), cb);
	}
	
	public static <T, E> AsyncTask<List<T>, E> Series(AsyncTask<T, E>... tasks) {
		return cb -> Asyncc.<T, E>Parallel(List.of(tasks), cb);
	}
	
	public static <T, E> AsyncTask<List<T>, E> Parallel(List<AsyncTask<T, E>> tasks) {
		return cb -> Asyncc.<T, E>Parallel(tasks, cb);
	}
	
	public static <T, E> AsyncTask<List<T>, E> Series(List<AsyncTask<T, E>> tasks) {
		return cb -> Asyncc.<T, E>Series(tasks, cb);
	}
	
	
	public static <T, E> AsyncTask<Map<String, T>, E> Parallel(Map<String, AsyncTask<T, E>> tasks) {
		return cb -> Asyncc.<T, E>Parallel(tasks, cb);
	}
	
	public static <T, E> AsyncTask<Map<String, T>, E> Series(Map<String, AsyncTask<T, E>> tasks) {
		return cb -> Asyncc.<T, E>Series(tasks, cb);
	}
	
	public static <V, T, E> void Map(List<?> items, Mapper<V, T, E> m, IAsyncCallback<List<T>, E> f) {
		
		List<T> results = new ArrayList<T>(Collections.<T>nCopies(items.size(), null));
		Counter c = new Counter();
		ShortCircuit s = new ShortCircuit();
		
		for (int i = 0; i < items.size(); i++) {
			
			final int val = c.getStartedCount();
			c.incrementStarted();
			KeyValue<V> kv = new KeyValue<V>(null, (V)items.get(i));
			
			m.map(kv, new AsyncCallback<T, E>(s) {
				@Override
				public void done(E e, T v) {
					
					if(s.isShortCircuited()){
						return;
					}
					
					if (e != null) {
						s.setShortCircuited(true);
						f.done((E) e, Collections.emptyList());  // List.of()?
						return;
					}
					
					c.incrementFinished();
					results.set(val, (T) v);
					
					if (c.getFinishedCount() == items.size()) {
						f.done(null, results);
					}
				}
				
			});
			
		}
		
	}
	
	public static <T, E> void Series(
		Map<String, AsyncTask<T, E>> tasks,
		IAsyncCallback<Map<String, T>, E> f) {
		
		Map<String, T> results = new HashMap<>();
		ShortCircuit s = new ShortCircuit();
		Counter c = new Counter();
		
		Iterator<Map.Entry<String, AsyncTask<T, E>>> entries = tasks.entrySet().iterator();
		Limit lim = new Limit(1);
		
		Asyncc.<T, E>RunMapLimit(entries, tasks, results, c, s, lim, f);
		
	}
	
	public static <T, E> void ParallelLimit(
		int limit,
		Map<String, AsyncTask<T, E>> tasks,
		IAsyncCallback<Map<String, T>, E> f) {
		
		Map<String, T> results = new HashMap<>();
		boolean error = false;
		Counter c = new Counter();
		ShortCircuit s = new ShortCircuit();
		
		Iterator<Map.Entry<String, AsyncTask<T, E>>> entries = tasks.entrySet().iterator();
		Limit lim = new Limit(limit);
		
		Asyncc.<T, E>RunMapLimit(entries, tasks, new HashMap<>(), c, s, lim, f);
		
	}
	
	public static <T, E> void Parallel(Map<String, AsyncTask<T, E>> tasks, IAsyncCallback<Map<String, T>, E> f) {
		
		Map<String, T> results = new HashMap<>();
		boolean error = false;
		Counter c = new Counter();
		ShortCircuit s = new ShortCircuit();
		
		for (Map.Entry<String, AsyncTask<T, E>> entry : tasks.entrySet()) {
			
			final String key = entry.getKey();
			
			entry.getValue().run(new AsyncCallback<T, E>(s) {
				
				@Override
				public void done(E e, T v) {
					
					if(s.isShortCircuited()){
						return;
					}
					
					if (e != null) {
						s.setShortCircuited(true);
						f.done(e, Map.of());
						return;
					}
					
					c.incrementFinished();
					results.put(key, v);
					
					if (c.getFinishedCount() == tasks.size()) {
						f.done(null, results);
					}
					
				}
			});
			
		}
		
	}
	
	@SuppressWarnings("Duplicates")
	public static <T, E> void Parallel(List<AsyncTask<T, E>> tasks, IAsyncCallback<List<T>, E> f) {
		
		List<T> results = new ArrayList<T>(Collections.<T>nCopies(tasks.size(), null));
		boolean error = false;
		Counter c = new Counter();
		ShortCircuit s = new ShortCircuit();
		
		for (int i = 0; i < tasks.size(); i++) {
			
			final int index = i;
			
			tasks.get(i).run(new AsyncCallback<T, E>(s) {
				@Override
				public void done(E e, T v) {
					
					if(s.isShortCircuited()){
						return;
					}
					
					if (e != null) {
						s.setShortCircuited(true);
						f.done(e, Collections.emptyList());
						return;
					}
					
					c.incrementFinished();
					results.set(index, v);
					
					if (c.getFinishedCount() == tasks.size()) {
						f.done(null, results);
					}
				}
			});
			
		}
		
	}
	
	
	public static <T, E> void ParallelLimit(
		int limit,
		List<AsyncTask> tasks,
		IAsyncCallback<List<T>, E> f) {
		
		Limit lim = new Limit(limit);
		ShortCircuit s = new ShortCircuit();
		List<T> results = new ArrayList<T>(Collections.<T>nCopies(tasks.size(), null));
		Counter c = new Counter();
		
		Asyncc.RunTasksLimit(tasks, results, c, s, lim, f);
		
	}
	
	private static <T, E> void RunMapLimit(
		Iterator<Map.Entry<String, AsyncTask<T, E>>> entries,
		Map<String, AsyncTask<T, E>> m,
		Map<String, T> results,
		Counter c,
		ShortCircuit s,
		Limit lim,
		IAsyncCallback<Map<String, T>, E> f) {
		
		if (c.getStartedCount() >= m.size()) {
			return;
		}
		
		if (!entries.hasNext()) {
			return;
		}
		
		Map.Entry<String, AsyncTask<T, E>> entry = entries.next();
		String key = entry.getKey();
		AsyncTask<T, E> t = entry.getValue();
		lim.increment();
		c.incrementStarted();
		
		t.run(new AsyncCallback<T, E>(s) {
			
			@Override
			public void done(E e, T v) {
				
				if(s.isShortCircuited()){
					return;
				}
				
				if (e != null) {
					s.setShortCircuited(true);
					f.done(e, Map.of());
					return;
				}
				
				results.put(key, v);
				lim.decrement();
				c.incrementFinished();
				
				if (c.getFinishedCount() == m.size()) {
					f.done(null, results);
					return;
				}
				
				if (lim.isBelowCapacity()) {
					Asyncc.RunMapLimit(entries, m, results, c, s, lim, f);
				}
			}
			
		});
		
		
		if (c.getStartedCount() >= m.size()) {
			return;
		}
		
		if (lim.isBelowCapacity()) {
			Asyncc.RunMapLimit(entries, m, results, c, s, lim, f);
		}
		
	}
	
	private static <T, E> void RunTasksLimit(
		List<AsyncTask> tasks,
		List<T> results,
		Counter c,
		ShortCircuit s,
		Limit lim,
		IAsyncCallback<List<T>, E> f) {
		
		if (c.getStartedCount() >= tasks.size()) {
//      f.run(null, results);
			return;
		}
		
		final int val = c.getStartedCount();
		AsyncTask<T,E> t = tasks.get(val);
		lim.increment();
		c.incrementStarted();
		
		t.run(new AsyncCallback<T, E>(s) {
			@Override
			public void done(E e, T v) {
				
				if(s.isShortCircuited()){
					return;
				}
				
				if (e != null) {
					s.setShortCircuited(true);
					f.done(e, Collections.emptyList());
					return;
				}
				
				results.set(val, v);
				lim.decrement();
				c.incrementFinished();
				
				if (c.getFinishedCount() == tasks.size()) {
					f.done(null, results);
					return;
				}
				
				if (lim.isBelowCapacity()) {
					Asyncc.RunTasksLimit(tasks, results, c, s, lim, f);
				}
				
			}
			
			
		});
		
		
		if (c.getStartedCount() >= tasks.size()) {
			return;
		}
		
		if (lim.isBelowCapacity()) {
			Asyncc.RunTasksLimit(tasks, results, c, s, lim, f);
		}
		
	}
	
	private static <T, E> void RunTasksSerially(
		List<AsyncTask<T, E>> tasks,
		List<T> results,
		ShortCircuit s,
		Counter c,
		IAsyncCallback<List<T>, E> f) {
		
		final int startedCount = c.getStartedCount();
		
		if (startedCount >= tasks.size()) {
//      f.run(null, results);
			return;
		}
		
		AsyncTask<T, E> t = tasks.get(startedCount);
		c.incrementStarted();
		
		t.run(new AsyncCallback<T, E>(s) {
			@Override
			public void done(E e, T v) {
				
				if(s.isShortCircuited()){
					return;
				}
				
				if (e != null) {
					s.setShortCircuited(true);
					f.done( e, Collections.emptyList());
					return;
				}
				
				c.incrementFinished();
				results.set(startedCount, v);
				
				if (c.getFinishedCount() == tasks.size()) {
					f.done(null, results);
					return;
				}
				
				Asyncc.RunTasksSerially(tasks, results, s, c, f);
			}
			
		});
		
	}
	
	public static <T, E> void Series(
		List<AsyncTask<T, E>> tasks,
		IAsyncCallback<List<T>, E> f) {
		
		List<T> results = new ArrayList<T>(Collections.nCopies(tasks.size(), null));
		Counter c = new Counter();
		ShortCircuit s = new ShortCircuit();
		
		if (tasks.size() < 1) {
			f.done(null, Collections.emptyList());
			return;
		}
		
		Asyncc.<T, E>RunTasksSerially(tasks, results, s, c, f);
	}
	
	public static interface IInjectable<T,E> {
	  public void run(AsyncCallbackSet<T,E> cb);
	}
	
	public static class Injectable<T,E> {
		
		Set<String> s;
		IInjectable i;
		
	  public Injectable(Set<String> s, IInjectable<T,E> i){
	  	this.s = s;
	  	this.i = i;
		}
		
		public Injectable(IInjectable<T,E> i){
			this.s = Set.of();
			this.i = i;
		}
		
		public Set<String> getSet(){
	  	return this.s;
		}
		
		public IInjectable getInjectable(){
	  	return this.i;
		}
	}
	
	public static <T, E> void checkForCircularDep(String key, Set<String> h, Map<String, Injectable<T, E>> tasks){
		
	    if(h.contains(key)){
	    	throw new Error("The following key has a circular dep: " + key);
			}
	  
	    for(String s: h){
	    	checkForCircularDep(key, tasks.get(s).getSet(), tasks);
			}
	}
	
	public static <T, E> void Inject(
		Map<String, Injectable<T, E>> tasks,
		IAsyncCallback<Map<String, Object>, E> f) {
		
		Counter c = new Counter();
		ShortCircuit s = new ShortCircuit();
		Map<String, Object> results = new HashMap<>();
		
		if (tasks.size() < 1) {
			f.done(null, Map.of());
			return;
		}
		
		for (Map.Entry<String, Injectable<T, E>> entry : tasks.entrySet()) {
			
			final String key = entry.getKey();
			final Set<String> set = entry.getValue().getSet();
			
			checkForCircularDep(key, set, tasks);
		}
		
		HashSet<String> started = new HashSet<>();
		HashSet<String> completed = new HashSet<>();
		
		Asyncc.<T, E>RunInject(started, completed, tasks, results, c, s, f);
	}
	
	public static abstract class AsyncCallbackSet<T, E> implements IAsyncCallback<T, E> {
		private ShortCircuit s;
		private Map<String, Object> values;
		
		public AsyncCallbackSet(ShortCircuit s, Map<String, Object> vals){
			this.s = s;
			this.values = vals;
		}
		
		public boolean isShortCircuited(){
			return this.s.isShortCircuited();
		}
		
		public Object get(String s){
			 return this.values.get(s);
		}
		
	}
	
	private static <T,E>void RunInject(
		HashSet<String> started,
		HashSet<String> completed,
		Map<String, Injectable<T, E>> m,
		Map<String, Object> results,
		Counter c,
		ShortCircuit s,
		IAsyncCallback<Map<String, Object>, E> f){
		
		
		for (Map.Entry<String, Injectable<T, E>> entry : m.entrySet()) {
			
			final String key = entry.getKey();
			final Set<String> set = entry.getValue().getSet();
			
			if(started.contains(key)){
				continue;
			}
			
			if(!completed.containsAll(set)){
				continue;
			}
			
			final IInjectable<T,E> v = entry.getValue().getInjectable();
			
			started.add(key);
			
			v.run(new AsyncCallbackSet<T,E>(s, results){
				
				@Override
				public void done(E err, T v) {
					
					if(s.isShortCircuited()){
						return;
					}
					
					if(err != null){
						s.setShortCircuited(true);
						return;
					}
					
					completed.add(key);
					results.put(key, v);
					
					if(completed.size() == m.size()){
						f.done(null, results);
						return;
					}
					
					RunInject(started,completed,m,results,c,s,f);
				}
			});
			
		}
	}
}
