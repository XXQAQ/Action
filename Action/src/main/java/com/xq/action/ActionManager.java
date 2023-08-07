package com.xq.action;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Pair;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ActionManager<T> {

    private final Context context;

    private final MethodCaller methodCaller = new MethodCaller();

    //核心记录Action相关的所有数据
    private final Map<String,ActionRecord> actionRecordMap = new HashMap<>();

    private final class ActionRecord{

        String name;
        Class<T> actionClass;
        Pair<Class<?>[],Object[]> constructorPair;

        public ActionRecord(String name, Class<T> actionClass){
            this(name,actionClass,null);
        }

        public ActionRecord(String name, Class<T> actionClass,Pair<Class<?>[],Object[]> constructorPair) {
            this.name = name;
            this.actionClass = actionClass;
            this.constructorPair = constructorPair;
        }
    }

    public ActionManager(Context context,String token) {
        this.context = context;
        try {
            Set<String> allActionClassPath = getAllActionClassPath(context,token);
            for (String path : allActionClassPath){
                try {
                    //
                    Class<T> actionClass = (Class<T>) Class.forName(path);
                    //
                    String name = actionClass.getAnnotation(ActionName.class).name();
                    //
                    ConstructorDeclare constructorDeclare = actionClass.getAnnotation(ConstructorDeclare.class);
                    if (constructorDeclare == null){
                        actionRecordMap.put(name,new ActionRecord(name,actionClass));
                    } else {
                        if (constructorDeclare.context()){
                            actionRecordMap.put(name,new ActionRecord(name,actionClass,new Pair<>(new Class<?>[]{Context.class},new Object[]{context})));
                        }
                    }

                } catch (ClassNotFoundException|NullPointerException|IllegalArgumentException e) {
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

    //name — Action
    private final Map<String, T> allActionMap = new HashMap<>();

    public void mallocAll(){
        try {
            for (String name : getAllActionNames()){
                malloc(name);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean malloc(String name){
        try {
            if (!allActionMap.containsKey(name)){
                ActionRecord actionRecord = actionRecordMap.get(name);
                allActionMap.put(name,constructorNewAction(actionRecord.actionClass,actionRecord.constructorPair));
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private T constructorNewAction(Class<T> cla,Pair<Class<?>[],Object[]> constructorPair) throws ClassCastException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        return cla.getDeclaredConstructor(constructorPair.first).newInstance(constructorPair.second);
    }

    public void freeAll(){
        if (!allActionMap.isEmpty()){
            for (T t : allActionMap.values()){
                try {
                    methodCaller.executeMethod(t,"onFree");
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                }
            }
            allActionMap.clear();
        }
    }

    public void free(String name){
        if (allActionMap.containsKey(name)){
            try {
                methodCaller.executeMethod(allActionMap.remove(name),"onFree");
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
    }

    public T getDefaultAction(){
        return getAction(getFirstEntry(actionRecordMap).getKey());
    }

    public T getAction(String name){
        return allActionMap.get(name);
    }

    public Context getContext(){
        return context;
    }

    public boolean containAction(String name){
        return actionRecordMap.containsKey(name);
    }

    public List<String> getAllActionNames(){
        return new ArrayList<>(actionRecordMap.keySet());
    }

    private <K,V>Map.Entry<K,V> getFirstEntry(Map<K,V> map){
        return (map == null || map.isEmpty())? null : new ArrayList<>(map.entrySet()).get(0);
    }

    private <K,V>Map.Entry<K,V> getLastEntry(Map<K,V> map){
        return (map == null || map.isEmpty())? null : new ArrayList<>(map.entrySet()).get(map.size()-1);
    }

}
