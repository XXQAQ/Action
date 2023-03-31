package com.xq.action;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Pair;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ActionManager {

    private final Context context;

    //核心记录Action相关的所有数据
    private final Map<String,ActionRecord> actionRecordMap = new HashMap<>();

    //当使用ability访问时，使用这个变量可以提高搜寻效率
    private final Map<String,TreeMap<Integer,String>> abilityPriorityMap = new HashMap<>();

    private final class ActionRecord{

        String name;
        String[] abilities;
        Class<?> actionClass;

        public ActionRecord(String name, String[] abilities, Class<?> actionClass) {
            this.name = name;
            this.abilities = abilities;
            this.actionClass = actionClass;
        }
    }

    public ActionManager(Context context,String token) {
        this.context = context;
        try {
            Set<String> allActionClassPath = getAllActionClassPath(context,token);
            for (String path : allActionClassPath){
                try {
                    Class<?> actionClass = Class.forName(path);
                    String name = actionClass.getAnnotation(ActionName.class).name();
                    String[] abilities = actionClass.getAnnotation(ActionAbility.class).abilities();
                    //
                    actionRecordMap.put(name,new ActionRecord(name,abilities,actionClass));
                    //
                    for (String ability : abilities){
                        if (!abilityPriorityMap.containsKey(ability)) {
                            abilityPriorityMap.put(ability,new TreeMap<Integer, String>());
                        }
                        abilityPriorityMap.get(ability).put(Math.abs(name.hashCode()),name);
                    }
                } catch (ClassNotFoundException|NullPointerException e) {
                    e.printStackTrace();
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private Set<String> getAllActionClassPath(Context context,String token) throws PackageManager.NameNotFoundException {
        Set<String> set = new HashSet<>();
        ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
        if (appInfo.metaData != null){
            for (String key : appInfo.metaData.keySet()){
                if (token.equals(appInfo.metaData.get(key))){
                    set.add(key);
                }
            }
        }
        return set;
    }

    public boolean initAll(String ability){
        if (abilityPriorityMap.containsKey(ability)){
            try {
                for (String name :abilityPriorityMap.get(ability).values()){
                    checkGetAction(name);
                }
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public boolean init(String name){
        try {
            checkGetAction(name);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public Pair<Boolean,Object> send(String name, String event, Object... data) {
        try {
            return new Pair<>(true,executeMethod(checkGetAction(name),event,data));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Pair<>(false,null);
    }

    public Pair<Boolean,Object> send(String name, String event, Map<String,?> extrasParam, Object... data){
        try {
            return new Pair<>(true,executeMethod(checkGetAction(name),event,extrasParam,data));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Pair<>(false,null);
    }

    public Pair<Boolean,Object> send(String name, String event, OnReceiveListener onReceiveListener, Object... data){
        try {
            return new Pair<>(true,executeMethod(checkGetAction(name),event,createAndSaveReceiveId(onReceiveListener),data));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Pair<>(false,null);
    }

    public Pair<Boolean,Object> send(String name, String event, OnReceiveListener onReceiveListener, Map<String,?> extrasParam, Object... data){
        try {
            return new Pair<>(true,executeMethod(checkGetAction(name),event,createAndSaveReceiveId(onReceiveListener),extrasParam,data));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Pair<>(false,null);
    }

    //Action实例化部分
    //name — Action
    private final Map<String, Action> allActionMap = new ConcurrentHashMap<>();

    private Action checkGetAction(String name) throws NullPointerException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, InstantiationException {
        if (!allActionMap.containsKey(name)){
            Action action = constructorNewAction(actionRecordMap.get(name).actionClass);
            action.init();
            allActionMap.put(name,action);
        }
        return allActionMap.get(name);
    }

    private Action constructorNewAction(Class<?> cla) throws ClassCastException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        return (Action) cla.getDeclaredConstructor(getClass()).newInstance(this);
    }

    public void releaseAll(){
        if (!allActionMap.isEmpty()){
            for (Action action : allActionMap.values()){
                action.release();
            }
            allActionMap.clear();
        }
    }

    public void releaseAll(String ability){
        if (abilityPriorityMap.containsKey(ability)){
            for (String name:abilityPriorityMap.get(ability).values()){
                if (allActionMap.containsKey(name)){
                    allActionMap.remove(name).release();
                }
            }
        }
    }

    public void release(String name){
        if (allActionMap.containsKey(name)){
            allActionMap.remove(name).release();
        }
    }

    //反射部分
    private Object executeMethod(Object object,String event,Object... data) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Object[] param = data;
        Method method = getMethodUseCache(object.getClass(),event,param);
        return method.invoke(object,param);
    }

    private Object executeMethod(Object object,String event,String receiveId,Object... data) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Object[] param = recombinationVariableArray(receiveId,data);
        Method method = getMethodUseCache(object.getClass(),event,param);
        return method.invoke(object,param);
    }

    private Object executeMethod(Object object,String event,Map<String,?> extrasParam,Object... data) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object[] param = recombinationVariableArray(extrasParam,data);
        Method method = getMethodUseCache(object.getClass(),event,param);
        return method.invoke(object,param);
    }

    private Object executeMethod(Object object,String event,String receiveId,Map<String,?> extrasParam,Object... data) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object[] param = recombinationVariableArray(receiveId,extrasParam,data);
        Method method = getMethodUseCache(object.getClass(),event,param);
        return method.invoke(object,param);
    }

    private final Map<String,Map<String,Method>> methodMap = new ConcurrentHashMap<>();
    private Method getMethodUseCache(Class<?> cla,String methodName,Object... param) throws NoSuchMethodException{
        String className = cla.getName();
        if (!methodMap.containsKey(className)){
            methodMap.put(className,new HashMap<String, Method>());
        }
        String methodDescription = getMethodDescription(methodName,param);
        if (!methodMap.get(className).containsKey(methodDescription)){
            methodMap.get(className).put(methodDescription,findMethod(getAllPublicMethod(cla),methodName,param));
        }
        return methodMap.get(className).get(methodDescription);
    }

    private String getMethodDescription(String methodName,Object... param){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(methodName);
        stringBuilder.append("(");
        for (int i=0;i<param.length;i++){
            stringBuilder.append(param[i] == null? "UnKnowClass" : param[i].getClass().getName());
            stringBuilder.append(",");
        }
        stringBuilder.deleteCharAt(stringBuilder.length()-1);
        stringBuilder.append(")");
        return stringBuilder.toString();
    }

    private Object[] recombinationVariableArray(Object... o){
        if (o == null || o.length == 0){
            return null;
        }
        int lastIndex = o.length-1;
        if (Array.get(o,lastIndex).getClass().isArray()){
            Object[] variableArray = new Object[lastIndex + Array.getLength(o[lastIndex])];
            System.arraycopy(o,0,variableArray,0,lastIndex);
            System.arraycopy(o[lastIndex],0,variableArray,lastIndex,Array.getLength(o[lastIndex]));
            return variableArray;
        }
        return o;
    }

    private Method[] getAllPublicMethod(Class<?> cla){
        return cla.getMethods();
    }

    private Method findMethod(Method[] methods,String methodName,Object... param) throws NoSuchMethodException{
        for (Method method : methods){
            if (method.getName().equals(methodName)){
                Class<?>[] methodParamClass = method.getParameterTypes();
                if (isSameType(methodParamClass,param)){
                    return method;
                }
            }
        }
        throw new NoSuchMethodException();
    }

    private static final Map<Class<?>, Class<?>> primitiveWrapperTypeMap = new IdentityHashMap<>(8);
    static {
        primitiveWrapperTypeMap.put(boolean.class,Boolean.class);
        primitiveWrapperTypeMap.put(byte.class,Byte.class);
        primitiveWrapperTypeMap.put(char.class,Character.class);
        primitiveWrapperTypeMap.put(double.class,Double.class);
        primitiveWrapperTypeMap.put(float.class,Float.class);
        primitiveWrapperTypeMap.put(int.class,Integer.class);
        primitiveWrapperTypeMap.put(long.class,Long.class);
        primitiveWrapperTypeMap.put(short.class,Short.class);
        primitiveWrapperTypeMap.put(void.class,Void.class);
    }

    private boolean isSameType(Class<?>[] typeClasses,Object[] objects){
        if (typeClasses == null && objects == null){
            return true;
        }
        if (typeClasses == null || objects == null || typeClasses.length != objects.length){
            return false;
        }
        for (int i=0;i<typeClasses.length;i++){
            Class<?> type = typeClasses[i];
            Object o = objects[i];
            if (o == null && type.isPrimitive()){
                return false;
            }
            if (type.isPrimitive() || primitiveWrapperTypeMap.containsValue(type)){
                if (!(type == o.getClass() || primitiveWrapperTypeMap.get(type) == o.getClass())){
                    return false;
                }
            } else {
                if (!type.isAssignableFrom(o.getClass())){
                    return false;
                }
            }
        }
        return true;
    }


    //接受部分
    private final Map<String, OnReceiveListener> onReceiveListenerMap = new ConcurrentHashMap<>();

    private String createAndSaveReceiveId(OnReceiveListener onReceiveListener){
        String receiveId = String.valueOf(nextReceiveId());
        onReceiveListenerMap.put(receiveId,onReceiveListener);
        return receiveId;
    }

    private final AtomicInteger RECEIVE_ID = new AtomicInteger(0);
    private int nextReceiveId() {
        return RECEIVE_ID.incrementAndGet();
    }

    public void receivePartialEvent(String receiveId,String event,Object... data){
        if (onReceiveListenerMap.containsKey(receiveId)){
            onReceiveListenerMap.get(receiveId).onPartialEvent(event,data);
        }
    }

    public void cancelReceive(String receiveId){
        if (onReceiveListenerMap.containsKey(receiveId)){
            onReceiveListenerMap.remove(receiveId);
        }
    }

    public void receiveResult(String receiveId,Object... data){
        if (onReceiveListenerMap.containsKey(receiveId)){
            onReceiveListenerMap.remove(receiveId).onResult(data);
        }
    }

    public void receiveError(String receiveId,String info,String code,String sourceWay,String sourceCode){
        if (onReceiveListenerMap.containsKey(receiveId)){
            onReceiveListenerMap.remove(receiveId).onError(info,code,sourceWay,sourceCode);
        }
    }

    //上报部分

    private final Map<OnReportListener, ListenerFilter> onReportListenerMap = new ConcurrentHashMap<>();

    public void reportEvent(String ability, String name, String event, Object... data){
        for (Map.Entry<OnReportListener,ListenerFilter> entry : onReportListenerMap.entrySet()){
            OnReportListener listener = entry.getKey();
            ListenerFilter listenerFilter = entry.getValue();
            if (listenerFilter == null){
                listener.onEvent(ability,name,event,data);
            }
            else    if (listenerFilter.isPass(ability,name,event)){
                listener.onEvent(ability,name,event,data);
            }
        }
    }

    public void reportError(String ability, String name, String info, String code, String sourceWay, String sourceCode){
        for (Map.Entry<OnReportListener,ListenerFilter> entry : onReportListenerMap.entrySet()){
            OnReportListener listener = entry.getKey();
            ListenerFilter listenerFilter = entry.getValue();
            if (listenerFilter == null){
                listener.onError(ability,name,info,code,sourceWay,sourceCode);
            }
            else    if (listenerFilter.isPass(ability,name,null)){
                listener.onError(ability,name,info,code,sourceWay,sourceCode);
            }
        }
    }

    public void registerOnReportListener(OnReportListener listener){
        registerOnReportListener(listener,null);
    }

    public void registerOnReportListener(OnReportListener listener,ListenerFilter listenerFilter){
        onReportListenerMap.put(listener,listenerFilter);
    }

    public void unregisterOnReportListener(OnReportListener listener){
        onReportListenerMap.remove(listener);
    }

    public interface ListenerFilter{
        public boolean isPass(String ability, String name, String event);
    }

    public static class SimpleListenerFilter implements ListenerFilter{

        private static final String FILTER_KEY_ABILITY = "ability";
        private static final String FILTER_KEY_NAME = "name";
        private static final String FILTER_KEY_EVENT= "event";

        private final Map<String,List<?>> filterListMap = new HashMap<>();

        public SimpleListenerFilter(String ability) {
            filterListMap.put(FILTER_KEY_ABILITY,Arrays.asList(ability));
        }

        public SimpleListenerFilter(String ability,String name) {
            filterListMap.put(FILTER_KEY_ABILITY,Arrays.asList(ability));
            filterListMap.put(FILTER_KEY_NAME,Arrays.asList(name));
        }

        public SimpleListenerFilter(String ability,String name,String event) {
            filterListMap.put(FILTER_KEY_ABILITY,Arrays.asList(ability));
            filterListMap.put(FILTER_KEY_NAME,Arrays.asList(name));
            filterListMap.put(FILTER_KEY_EVENT,Arrays.asList(event));
        }

        public SimpleListenerFilter(String[] abilities, String[] names, String[] events) {
            filterListMap.put(FILTER_KEY_ABILITY,Arrays.asList(abilities));
            filterListMap.put(FILTER_KEY_NAME,Arrays.asList(names));
            filterListMap.put(FILTER_KEY_EVENT,Arrays.asList(events));
        }

        @Override
        public boolean isPass(String ability,String name,String event){
            Map<String,String> map = new HashMap<>();
            map.put(FILTER_KEY_ABILITY,ability);
            map.put(FILTER_KEY_NAME,name);
            map.put(FILTER_KEY_EVENT,event);
            for (Map.Entry<String,List<?>> entry : filterListMap.entrySet()){
                String filterKey = entry.getKey();
                List<?> filterData = entry.getValue();
                if (map.containsKey(filterKey)){
                    if (!filterData.contains(map.get(filterKey))){
                        return false;
                    }
                }
            }
            return true;
        }
    }

    //一些获取

    public Context getContext(){
        return context;
    }

    public boolean containAction(String name){
        return actionRecordMap.containsKey(name);
    }

    public boolean containAbility(String ability){
        return abilityPriorityMap.containsKey(ability);
    }

    public String getDefaultActionName(String ability){
        if (abilityPriorityMap.containsKey(ability)){
            Map.Entry<Integer,String> entry = getLastEntry(abilityPriorityMap.get(ability));
            return entry == null?null:entry.getValue();
        }
        return null;
    }

    public List<String> getAllActionNames(String ability){
        if (abilityPriorityMap.containsKey(ability)){
            return new ArrayList<String>(abilityPriorityMap.get(ability).values());
        }
        return new ArrayList<>();
    }

    private <K,V>Map.Entry<K,V> getFirstEntry(Map<K,V> map){
        return (map == null || map.isEmpty())? null : new ArrayList<>(map.entrySet()).get(0);
    }

    private <K,V>Map.Entry<K,V> getLastEntry(Map<K,V> map){
        return (map == null || map.isEmpty())? null : new ArrayList<>(map.entrySet()).get(map.size()-1);
    }

}
