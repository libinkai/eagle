package com.equator.eagle.threadpool;


import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: Equator
 * @Date: 2019/12/4 20:39
 **/

public class DefaultThreadPool<Job extends Runnable> implements EagleThreadPool<Job> {
    // 线程池工作者数目
    private static final int maxWorkerNumber = 16;
    private static final int defaultWorkerNumber = 4;
    private static final int minWorkerNumber = 1;
    // 工作者编号（线程名）
    private AtomicInteger workerId = new AtomicInteger();
    // 工作者队列
    private final List<Worker> workerList = new LinkedList<>();
    // 工作任务队列
    private final List<Job> jobList = new LinkedList<>();

    // 工作者内部类
    class Worker implements Runnable {
        private volatile boolean isRunning = true;
        private volatile boolean isHandling = false;

        @Override
        public void run() {
            while (isRunning) {
                Job job = null;
                synchronized (jobList) {
                    while (jobList.isEmpty()) {
                        try {
                            jobList.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    // 取一个任务
                    job = jobList.remove(0);
                    System.out.println("取一个任务，剩余：" + jobList.size());
                }
                if (job != null) {
                    try {
                        isHandling = true;
                        job.run();
                    } catch (Exception e) {
                    } finally {
                        isHandling = false;
                    }
                }
                System.out.println("worker is running");
            }
        }

        public void close() {
            this.isRunning = false;
        }
    }

    public DefaultThreadPool() {
        initWorkers(defaultWorkerNumber);
    }

    public DefaultThreadPool(int initialWorkerNumber) {
        initWorkers(initialWorkerNumber);
    }

    public int initWorkers(int num) {
        int freeCapacity = maxWorkerNumber - workerList.size();
        if (num >= freeCapacity) {
            num = freeCapacity;
        }
        if (num < minWorkerNumber) {
            num = minWorkerNumber;
        }
        for (int i = 0; i < num; i++) {
            Worker worker = new Worker();
            workerList.add(worker);
            Thread thread = new Thread(worker, "Worker-" + workerId.incrementAndGet());
            thread.start();
        }
        return 0;
    }

    @Override
    public void execute(Job job) {
        if (job != null) {
            synchronized (jobList) {
                jobList.add(job);
                jobList.notify();
            }
        }
    }

    @Override
    public int getJobSize() {
        return jobList.size();
    }

    @Override
    public int addWorkers(int num) {
        synchronized (jobList) {
            return initWorkers(num);
        }
    }

    @Override
    public int removeWorkers(int num) {
        int count = 0;
        synchronized (jobList) {
            for (int i = 0; i < num; i++) {
                Worker worker = workerList.get(i);
                if (!worker.isHandling) {
                    worker.close();
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public void shutdown() {
        System.out.println("thread pool try shutdown ...");
        while (!jobList.isEmpty()) {
            System.out.println("jobList not null, jobList size : " + jobList.size());
        }
        System.out.println("jobList is null, jobList size : " + jobList.size());
        int count = 0;
        for (Worker worker : workerList) {
            worker.close();
            System.out.println("close worker " + ++count);
        }
        System.out.println("thread pool shutdown ...");
        count = 0;
        for (Worker worker : workerList) {
            System.out.println("worker "+count+" "+worker.isRunning+" "+worker.isHandling);
        }
    }

    public static void main(String[] args) {
        System.out.println("start");
        DefaultThreadPool defaultThreadPool = new DefaultThreadPool();
        int count = 10;
        while (count > 0) {
            int finalCount = count;
            defaultThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    int flag = 10;
                    while (flag > 0) {
                        System.out.println("thread " + finalCount + " : " + flag);
                        flag--;
                    }
                }
            });
            count--;
        }
        System.out.println("job added ...");
        defaultThreadPool.shutdown();
        System.out.println("jobListSize " + defaultThreadPool.jobList.size());
        System.out.println("workerListSize " + defaultThreadPool.workerList.size());
        System.out.println("close");
    }
}
