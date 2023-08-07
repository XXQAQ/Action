package com.xq.action;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class MethodCaller {

    public Object executeMethod(Object object,String event,Object... data) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Object[] param = data;
        Method method = getMethodUseCache(object.getClass(),event,param);
        return method.invoke(object,param);
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

}
