/**
 * 
 */
package com.liuyang.thread;

import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

/**
 * 简易线程池
 * @author liuyang
 * @version 1.0.0
 *
 */
public class SimpleThreadPool<T> {
	/**最大的线程数量**/
	//public final static int MAX_THREADPOOL_NUMBER = 128;
	//private final static Thread[] DEF_THREADPOOLS = new Thread[MAX_THREADPOOL_NUMBER];
	//private static int HAD_THREADPOOL_NUMBER = 0;
	
	private int limit, useableNum = 0;
	private int waitDelay = 100;
	private ThreadResultCallBack<T> callback;
	private Thread[] threads = null;
	private int[] arrayThreadId = null;
	private List<ThreadPoolHandler> handler = null;
	private Map<String, Callable<T>> commands = new LinkedHashMap<String, Callable<T>>();
	private boolean bWaitForCommand = false;
	private boolean bThreadTerminal = false;
	
	/**
	 * 简易线程池初始化
	 * @param limit 线程池最大线程数量
	 * @param callback 线程池程序回调处理接口
	 */
	public SimpleThreadPool(int limit, ThreadResultCallBack<T> callback) {
		//int remains = (MAX_THREADPOOL_NUMBER - HAD_THREADPOOL_NUMBER);
    	this.limit = limit; //remains > limit ? limit : remains;
    	this.handler = new ArrayList<ThreadPoolHandler>();
    	this.callback = callback != null ? callback : new ThreadResultCallBack<T>() {
			@Override
			public void callback(String threadId, T result) {
				
			};
    	};
    	// 初始化线程池
    	this.threads = new Thread[this.limit];
    	for(int i = 0; i < this.limit; i++) {
    		this.handler.add(new ThreadPoolHandler(i));
    		//this.threads[i] = new Thread(new ThreadPoolHandler(i));
    	}

    }
    
	/**
	 * 简易线程池初始化
	 * @param limit 线程池最大线程数量
	 */
    public SimpleThreadPool(int limit) {
    	this(limit, null);
    }
    
    protected void finalize() {
    	//handler.clear();
    	commands.clear();
    	limit = 0;
    	callback = null;
    	//handler = null;
    	commands = null;
    	arrayThreadId = null;
    }
    
    public int getRemainCommands() {
    	synchronized(commands) {
    		return commands.size();
    	}
    }
    
    /**
     * 提交线程指令实例
     * @param threadId
     * @param command
     */
    public synchronized void submit(String threadId, Callable<T> command) {
    	synchronized(commands) {
    		commands.put(threadId, command);
    	}
    }
    
    public synchronized void submit(Callable<T> command) {
        synchronized(commands) {
            long timeMillis = System.currentTimeMillis();
            long timeNanos = System.nanoTime();
            commands.put(timeMillis + "_" + timeNanos, command);
        }
    }
    
    private Thread createThread(int i) {
    	//Thread newThread = new Thread(handler.get(i).reset());
    	Thread newThread = new Thread(new ThreadPoolHandler(i));
    	//Thread newThread = new Thread(new ThreadPoolHandler(arrayThreadId[i]));
    	newThread.start();
		return newThread;
    }
    
    public synchronized void start() {
    	/*synchronized(DEF_THREADPOOLS) {
        	bThreadTerminal = false; 
        	for(int i = 0; i < useableNum; i++) {
        		int id = arrayThreadId[i];
        		if (DEF_THREADPOOLS[id] == null) {
        			DEF_THREADPOOLS[id]= createThread(i);
        		}
        	}
    	}*/

    	for(int i = 0; i < this.limit; i++) {
    		if (threads[i] == null) threads[i] = createThread(i);
    	}
    }
    
    public synchronized void restart() {
    	/*synchronized(DEF_THREADPOOLS) {
        	bThreadTerminal = false; 
        	for(int i = 0; i < useableNum; i++) {
        		int id = arrayThreadId[i];
        		if (DEF_THREADPOOLS[id] == null) {
        			DEF_THREADPOOLS[id] = createThread(i);
        		} else if (DEF_THREADPOOLS[id].isAlive() == false){
        			DEF_THREADPOOLS[id] = createThread(i);
        		}
        	}
    	}*/
    	bThreadTerminal = false; 
    	for(int i = 0; i < this.limit; i++) {
    		if (threads[i] == null) {
    			threads[i] = createThread(i);
    		} else if (threads[i].isAlive() == false) {
    			threads[i] = createThread(i);
    		}
    	}
    }
    
    public void stop() {
    	bThreadTerminal = true;
    }
    
    public void waitFroCommand(boolean flag) {
    	bWaitForCommand = flag;
    }
    
    private class Node<K, V> {
    	private K key;
    	private V value;
    	

    	public Node(K key, V value) {
    		this.key = key;
    		this.value = value;
    	}
    	
    	protected void finalize() {
    		key = null;
    		value = null;
    	}
    	
    	public K getKey() {
    		return key;
    	}
    	
    	public V getValue() {
    		return value;
    	}
    }
    
    private class ThreadPoolHandler implements Runnable {
    	/**线程轮循开关*/
    	private boolean bContiune = true;
    	
    	private int id;
    	private String runningThreadName = null;
    	
    	public ThreadPoolHandler(int threadId) {
    		this.id = threadId;
    	}

    	protected void finalize() {
    		id = 0;
    		runningThreadName = null;
    		bContiune = false;
    	}
    	/**
    	 * 获取可执行程序
    	 * @return
    	 */
        private Node<String, Callable<T>> getNextCommand() {
            synchronized (commands) {
            	Node<String, Callable<T>> nextCommand = null;
            	Iterator<Entry<String, Callable<T>>> itor = commands.entrySet().iterator();
                while(itor.hasNext()) {
                    Entry<String, Callable<T>> command = itor.next();
                    nextCommand = new Node<String, Callable<T>> (command.getKey(), command.getValue());
                    itor.remove();
                    break;
                }
                return nextCommand;
            }
        }
        
        public synchronized int getId() {
        	return id;
        }
        
        public synchronized String getRunningThreadName() {
        	return runningThreadName;
        }
        
		@Override
		public synchronized void run() {
			while (bContiune == true) {
				Node<String, Callable<T>> next = getNextCommand();
				T result = null;
				// 判断是否有可执行程序
				bContiune = next != null;
				// 判断是否等待
				bContiune = bWaitForCommand ? true : bContiune;
				// 判断是否终止线程
				bContiune = bThreadTerminal ? false : bContiune;
				if (next != null) {
					try {
						runningThreadName = next.getKey();
	                    if (next.getValue() != null) {
								result = next.getValue().call();
	                        // 回调处理返回值
	                        if(callback != null) callback.callback(next.getKey(), result);
	                    }
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
	                    next = null;
	                    result = null;
					}
				} else {
					// 在无可执行程序的情况下，判断是否需要等待
					if (bContiune && bWaitForCommand) {
						try {
	                        Thread.currentThread();
	                        Thread.sleep(waitDelay); // 线程修眠100ms
	                    } catch (InterruptedException e) {
	                        e.printStackTrace();
	                    } 
					}
				}
			}
			// 从线程池中移除
			if (threads != null) threads[id] = null;
	    	/*synchronized(DEF_THREADPOOLS) {
	    		DEF_THREADPOOLS[id] = null;
	    	}*/
		}
		
		public synchronized ThreadPoolHandler reset() {
    		id = 0;
    		runningThreadName = null;
    		bContiune = true;
    		return this;
		}
    }
}
