package com.liuyang.thread;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

/**
 * 线程池
 * @author liuyang
 * @version 1.2.0
 * @date 2017-07-16
 * @last 2017-12-19
 * @param <T>
 */
public class FixedThreadPool<T> {
    /**
     * 待执行的命令
     */
    private Map<String, Callable<T>> commandCollectionWait = null;
    /**
     * 可执行的命令
     */
    private Map<String, Callable<T>> commandCollectionExecute = null;
    /**
     * 线程返回值处理回调程序
     */
    private ThreadResultCallBack<T> resultCallBack = null;
    /**
     * 多线程列表
     */
    private List<Thread> executeThreadList = null;
    /**
     * 用于判断线程是否等待新命令的执行
     */
    private boolean bWaitForNewCommand = true;
    /**
     * 用于终止所有线程过程
     */
    private boolean bAllThreadTerminal = false;
    private boolean bThreadAlreadyStart = false;
    
    private int thread_running_number = 0;
    /**
     * 线程池初始化, 指定多线程数量
     * @param maxThreadLimit
     */
    public FixedThreadPool(int maxThreadLimit) {
        // LinkedHashMap 可以按顺序读取
        commandCollectionWait = Collections.synchronizedMap(new LinkedHashMap<String, Callable<T>>());
        commandCollectionExecute  = Collections.synchronizedMap(new LinkedHashMap<String, Callable<T>>());
        if (maxThreadLimit > 0 ) {
            // 初始线线程池中的线程
            executeThreadList = new ArrayList<Thread>();
            for(int i = 0; i < maxThreadLimit; i++) {
                executeThreadList.add( null );
            }
            
        }

    }
    public FixedThreadPool(int maxThreadLimit, ThreadResultCallBack callback) {
        // LinkedHashMap 可以按顺序读取
        commandCollectionWait = Collections.synchronizedMap(new LinkedHashMap<String, Callable<T>>());
        commandCollectionExecute  = Collections.synchronizedMap(new LinkedHashMap<String, Callable<T>>());
        if (maxThreadLimit > 0 ) {
            // 初始线线程池中的线程
            executeThreadList = new ArrayList<Thread>();
            for(int i = 0; i < maxThreadLimit; i++) {
                executeThreadList.add( null );
            }
            resultCallBack = callback;
        }

    }
    /**
     * 线程销毁时，清理内存
     */
    protected synchronized void finalize() throws IOException {
        this.stop();
        commandCollectionWait.clear();
        commandCollectionExecute.clear();
        executeThreadList.clear();
        commandCollectionWait = null;
        commandCollectionExecute = null;
        executeThreadList = null;
        resultCallBack = null;
        //System.gc();
    }
    
    private String now() {
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss");
        return String.format("%s.%03d", formatter.format(date), date.getTime() % 1000);
    }
    
    /**
     * 提交线程命令, 但需要指定线程标识。提交的线程命令将处理等待状态。可以指定处理返回值的接口实例。
     * @param <T>
     * @param threadId
     * @param command
     */
    public synchronized FixedThreadPool<T> submit(String threadId, Callable<T> command, ThreadResultCallBack callBack) {
        synchronized(commandCollectionWait) {
            commandCollectionWait.put(threadId, command);
        }
        //synchronized(resultCallBack) {
        resultCallBack = callBack;
        //}
        return this;
    }
    /**
     * 提交线程命令, 但需要指定线程标识。提交的线程命令将处理等待状态。
     * @param <T>
     * @param threadId
     * @param command
     */
    public synchronized FixedThreadPool<T> submit(Callable<T> command) {
        long timeMillis = System.currentTimeMillis();
        long timeNanos = System.nanoTime();
        synchronized(commandCollectionWait) {
            commandCollectionWait.put(timeMillis + "_" + timeNanos, command);
        }
        return this;
    }
    /**
     * 提交线程命令, 但需要指定线程标识。提交的线程命令将处理等待状态。
     * @param <T>
     * @param threadId
     * @param command
     */
    public synchronized FixedThreadPool<T> submit(String threadId, Callable<T> command) {
        synchronized(commandCollectionWait) {
            commandCollectionWait.put(threadId, command);
        }
        return this;
    }
    /**
     * 将处理等待状态的线程推入可执行线程列表
     */
    public synchronized FixedThreadPool<T> commit() {
        synchronized (commandCollectionWait) { // 同步锁定
            synchronized(commandCollectionExecute) { // 同步锁定
                for(Iterator<Entry<String, Callable<T>>> itor = commandCollectionWait.entrySet().iterator(); itor.hasNext(); ) {
                    Entry<String, Callable<T>> next = itor.next();
                    if (commandCollectionExecute.containsKey(next.getKey()) == false) {
                        commandCollectionExecute.put(next.getKey(), next.getValue());
                    }
                    itor.remove();
                }
            }
            commandCollectionWait.clear();
        }
        return this;
    }
    public synchronized boolean has(String threadId) {
        return commandCollectionExecute.containsKey(threadId) || commandCollectionWait.containsKey(threadId);
    }
    /**
     * 是否等待将命令的注入
     * @param flag
     * @return
     */
    public synchronized boolean wait(boolean flag) {
        bWaitForNewCommand = flag;
        return bWaitForNewCommand;
    }
    /**
     * 获知还剩余多少命令未执行
     * @return
     */
    public synchronized int getRemain() {
        int remains = 0;
        synchronized (commandCollectionExecute) {
            remains = commandCollectionExecute.size();
        }
        return remains;
    }
    /**
     * 获知还有多少线程还在执行。正在执行的线程数据不会超过初始设定的线程最大限数。
     * @return
     */
    public int getRunning() {
        int runnings = 0;
        for(int i = 0; i < executeThreadList.size(); i++) {
            runnings += executeThreadList.get(i).isAlive() ? 1 : 0;
        }
        return runnings;
    }
    /**
     * 停止所有线程
     */
	public synchronized void stop() {
        bWaitForNewCommand = false; // 停止等持新的指令
        bAllThreadTerminal = true; // 终止所有线程过程
        /*for(int i = 0; i < executeThreadList.size(); i++) {
        }*/
    }
    
	public synchronized void close() {
        this.stop();
        commandCollectionWait.clear();
        commandCollectionExecute.clear();
        executeThreadList.clear();
	}
	
	/**
     * 所有线程开启
     */
    public synchronized void start() {
    	synchronized(this) {
            bAllThreadTerminal = false; // 所有线程过程可以启动
            if (bThreadAlreadyStart == false) {
                bThreadAlreadyStart = true;
                for(int i = 0; i < executeThreadList.size(); i++) {
                    Thread whichThread = null;
                    if (executeThreadList.get(i) == null) {
                        whichThread = new Thread(new Thread(new ThreadPoolProcHandler()));
                        executeThreadList.set(i, whichThread);
                        //System.out.println("添加新线程：" + whichThread);
                        whichThread.start();
                    }
                    whichThread = null;
                }
            } else {
                restart();
            }
    	}
    }
    
    /**
     * 重启停止的线程
     */
    public synchronized void restart() {
    	synchronized(this) {
            if (bThreadAlreadyStart == true) {
            	for(int i = 0; i < executeThreadList.size(); i++) {
                    Thread whichThread = null;
                    if (executeThreadList.get(i) == null) {
                        whichThread = new Thread(new Thread(new ThreadPoolProcHandler()));
                        executeThreadList.set(i, whichThread);
                        //System.out.println("添加新线程：" + whichThread);
                        whichThread.start();
                    } else if (executeThreadList.get(i).isAlive() == false) {
                    	//System.out.println(executeThreadList.get(i) + " is dead");
                    	whichThread = new Thread(new Thread(new ThreadPoolProcHandler()));
                    	executeThreadList.set(i, whichThread);
                    	whichThread.start();
                    }
                }
            } else {
            	//System.out.println("线程池还未开启，请运行start");
            }
    	}
    }
    /**
     * 处理多线程命令, 从线程缓冲池中获取命令并执行, 直至缓冲池再无新的命令注入; 
     * @author liuyang
     * @version 1.0.0
     * @date 2017-07-16
     */
    private class ThreadPoolProcHandler implements Runnable {
    	private Map<String, Callable<T>> nextCommand = null;
        private boolean bContiune = true;
        private Thread tread;

        
        // 调取下一个命令
        private synchronized Map<String, Callable<T>> getNextCommand() {
            String executeCommandId = null;
            Map<String, Callable<T>> executeCommand = null;
            //if (commandCollectionExecute != null) {
                synchronized (commandCollectionExecute) {
                	Iterator<Entry<String, Callable<T>>> itor = null;
                    for(itor = commandCollectionExecute.entrySet().iterator(); itor.hasNext(); ) {
                        Entry<String, Callable<T>> executeCommandMap = itor.next();
                        executeCommandId = executeCommandMap.getKey();
                        executeCommand = new HashMap<String, Callable<T>> ();
                        executeCommand.put(executeCommandId, executeCommandMap.getValue());
                        itor.remove();
                        break;
                    }
                }
            //}
            return executeCommand;
        }
        @Override
        public synchronized void run() {
            
            thread_running_number++;
            //System.out.println("线程正在运行：" + Thread.currentThread());
            //if (thread_running_number == 1) System.out.println("[" + now() + "] FixedThreadPool.noties: all threads will be execute.");
            // 当没有可执行的命令时, 退出该线程;
            while (bContiune == true) {
            	// 判断是否可以获取命令
                bContiune = (nextCommand = getNextCommand()) != null;
                // 判断是否要等待命令。如果需要等待，则 bContiune=true。
                bContiune = bWaitForNewCommand ? bWaitForNewCommand : bContiune;
                // 判断是否终止执行(优先级最高)。如果终止执行，则 bContiune=false。
                bContiune = bAllThreadTerminal ? false : bContiune;
                if (bContiune == true && nextCommand != null) {
                	try {
                        if (nextCommand.size() > 0) {
                            Entry<String, Callable<T>> next = nextCommand.entrySet().iterator().next();
                            T result = null;
                            if (next.getValue() != null) {
                                result = next.getValue().call();
                                // 回调处理返回值
                                if(resultCallBack != null) {
                                    resultCallBack.callback(next.getKey(), result);
                                }
                            }
                            nextCommand.clear();
                            nextCommand = null;
                            next = null;
                            result = null;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                    	thread_running_number--;
                        if (nextCommand != null) {
                            nextCommand.clear();
                            nextCommand = null;
                        }
                        /*try {
                        	//Thread.currentThread().join();
                        	//System.out.println("线程等待：" + Thread.currentThread());
                            Thread.currentThread();
                            Thread.sleep(100); // 线程修眠200ms
                            //System.gc(); // 尝试释放无用资源
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }*/
                    }
                } else {
                	//System.out.println("线程结束：" + Thread.currentThread());
                	
                	//if (thread_running_number <= 0) System.out.println("[" + now() + "] FixedThreadPool.noties: all threads has been finished.");
                	break;
                }
            }
        }
        
    }
}
