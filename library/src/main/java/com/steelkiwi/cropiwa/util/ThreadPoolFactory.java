package com.steelkiwi.cropiwa.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolFactory {

    private static ThreadPoolFactory mThreadPoolFactory;
    private ExecutorService mThreadPool;

    private ThreadPoolFactory(){
        mThreadPool = new ThreadPoolExecutor(5, 200,0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(1024),  new ThreadPoolExecutor.AbortPolicy());
    }

    public static ThreadPoolFactory getInstance() {
        if (mThreadPoolFactory == null) {
            synchronized (ThreadPoolFactory.class) {
                if (mThreadPoolFactory == null) {
                    mThreadPoolFactory = new ThreadPoolFactory();
                }
            }
        }
        return mThreadPoolFactory;
    }

    public void startRunnable(Runnable runnable){
        if(mThreadPool != null){
            mThreadPool.execute(runnable);
        }
    }
}
