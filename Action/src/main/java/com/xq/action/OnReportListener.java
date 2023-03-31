package com.xq.action;

public interface OnReportListener {

    public void onEvent(String ability, String name, String event, Object... data);

    public void onError(String ability, String name, String info, String code, String sourceWay, String sourceCode);

}
