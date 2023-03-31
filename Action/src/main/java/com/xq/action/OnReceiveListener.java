package com.xq.action;

public interface OnReceiveListener {

    public void onPartialEvent(String event, Object... data);

    public void onResult(Object... data);

    public void onError(String info, String code, String sourceWay, String sourceCode);

}
