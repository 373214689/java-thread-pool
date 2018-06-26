package com.liuyang.thread;

public interface ThreadResultCallBack<T> {
	/**
	 * Handle result
	 * @param threadId
	 * @param result
	 * @return
	 */
    public abstract void callback(String threadId, T result);

}
