package io.javalin.util;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrencyUtil {
    public static boolean useLoom = true;

    public static ExecutorService executorService(String name){
        return useLoom && LoomUtil.loomAvailable ?
            LoomUtil.getExecutorService(name) :
            Executors.newCachedThreadPool(new NamedThreadFactory(name));
    }

    public static ScheduledExecutorService newSingleThreadScheduledExecutor(String name) {
        return Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(name));
    }

    public static ThreadPool jettyThreadPool(String name){
        if (useLoom && LoomUtil.loomAvailable) {
            return new LoomThreadPool(name);
        }else {
            QueuedThreadPool threadPool =  new QueuedThreadPool(250, 8, 60_000);
            threadPool.setName(name);
            return threadPool;
        }
    }


    private static class LoomThreadPool implements ThreadPool {

        private String name;

        public LoomThreadPool(String name) {
            this.name = name;
        }

        private final ExecutorService executorService = LoomUtil.getExecutorService(name);

        @Override
        public void join() throws InterruptedException {

        }

        @Override
        public int getThreads() {
            return 1;
        }

        @Override
        public int getIdleThreads() {
            return 1;
        }

        @Override
        public boolean isLowOnThreads() {
            return false;
        }

        @Override
        public void execute(@NotNull Runnable command) {
            executorService.submit(command);
        }
    }
    public static class LoomUtil {

        public static boolean loomAvailable;

        static {
            try {
                Thread.ofVirtual(); // this will throw if preview is not enabled
                loomAvailable = true;
            } catch (UnsupportedOperationException e) {
                loomAvailable = false;
            }
        }

        static ExecutorService getExecutorService(String name) {
            if (!loomAvailable) {
                //TODO Check if this is the right way to handle this, and if the variable is set at runtime
                throw new IllegalArgumentException("Your Java version ("+System.getProperty("java.version")+") supports Loom, but preview features are disabled.");
            }
            return Executors.newThreadPerTaskExecutor(new NamedThreadFactory(name));
        }

        final static String LOG_MSG = "Your JDK supports Loom. Javalin will prefer Virtual Threads by default. Disable with `ConcurrencyUtil.useLoom = false`.";

        public void logIfLoom(Server server){
            if(server.getThreadPool() instanceof LoomThreadPool)
                JavalinLogger.startup(LOG_MSG);
        }
    }

    private static class NamedThreadFactory implements ThreadFactory {

        private final String prefix;

        public NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        private final ThreadGroup group = Thread.currentThread().getThreadGroup();
        private AtomicInteger threadCount = new AtomicInteger(0);

        @Override
        public Thread newThread(@NotNull Runnable r) {
            return new Thread(group, r, prefix + "-" + threadCount.getAndIncrement(), 0);
        }

    }

    private static class NamedVirtualThreadFactory implements ThreadFactory {

            private final String prefix;

            public NamedVirtualThreadFactory(String prefix) {
                this.prefix = prefix;
            }

            private AtomicInteger threadCount = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return Thread.ofVirtual().name(prefix + "-Virtual-" + threadCount.getAndIncrement()).unstarted(r);
            }

    }

}
