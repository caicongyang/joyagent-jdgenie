package com.jd.genie.agent.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * 线程工具类 - 为工作流并行执行提供线程管理
 */
@Slf4j
public class ThreadUtil {
    
    /**
     * 默认线程池
     */
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("WorkflowThread-" + System.currentTimeMillis());
        t.setDaemon(true);
        return t;
    });
    
    /**
     * 自定义线程池
     */
    private static ExecutorService customExecutor = null;
    
    /**
     * 设置自定义线程池
     */
    public static void setCustomExecutor(ExecutorService executor) {
        customExecutor = executor;
    }
    
    /**
     * 获取当前使用的线程池
     */
    public static ExecutorService getExecutor() {
        return customExecutor != null ? customExecutor : DEFAULT_EXECUTOR;
    }
    
    /**
     * 异步执行任务
     */
    public static Future<?> execute(Runnable task) {
        return getExecutor().submit(task);
    }
    
    /**
     * 异步执行任务并返回结果
     */
    public static <T> Future<T> execute(Callable<T> task) {
        return getExecutor().submit(task);
    }
    
    /**
     * 创建CountDownLatch
     */
    public static CountDownLatch getCountDownLatch(int count) {
        return new CountDownLatch(count);
    }
    
    /**
     * 等待CountDownLatch完成
     */
    public static void await(CountDownLatch latch) throws InterruptedException {
        latch.await();
    }
    
    /**
     * 等待CountDownLatch完成（带超时）
     */
    public static boolean await(CountDownLatch latch, long timeout, TimeUnit unit) throws InterruptedException {
        return latch.await(timeout, unit);
    }
    
    /**
     * 创建信号量
     */
    public static Semaphore getSemaphore(int permits) {
        return new Semaphore(permits);
    }
    
    /**
     * 等待所有Future完成
     */
    public static void waitForAll(Future<?>... futures) throws ExecutionException, InterruptedException {
        for (Future<?> future : futures) {
            future.get();
        }
    }
    
    /**
     * 等待所有Future完成（带超时）
     */
    public static void waitForAll(long timeout, TimeUnit unit, Future<?>... futures) 
            throws ExecutionException, InterruptedException, TimeoutException {
        for (Future<?> future : futures) {
            future.get(timeout, unit);
        }
    }
    
    /**
     * 休眠指定时间
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Thread sleep interrupted", e);
        }
    }
    
    /**
     * 关闭线程池
     */
    public static void shutdown() {
        if (customExecutor != null) {
            customExecutor.shutdown();
            try {
                if (!customExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    customExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                customExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 立即关闭线程池
     */
    public static void shutdownNow() {
        if (customExecutor != null) {
            customExecutor.shutdownNow();
        }
    }
}