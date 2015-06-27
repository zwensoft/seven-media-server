package org.zwen.media;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Threads {
	public static final ExecutorService POOLS = Executors.newCachedThreadPool();
	
	public static Future<?> submit(Runnable task){
		return POOLS.submit(task);
	}
	
	public static <V> Future<V> submit(Callable<V> task){
		return POOLS.submit(task);
	}
}
