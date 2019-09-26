package com.vane.eventbusdemo;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Phaser;

public class EventBus {

    private Map<Object, List<SubscribleMethod>> cacheMap;
    public static volatile EventBus instance;

    private Handler handler;

    public EventBus() {
        cacheMap = new HashMap<>();
        handler = new Handler();
    }

    public static EventBus getDefault() {
        if (instance == null){
            synchronized (EventBus.class){
                if (instance == null){
                    instance = new EventBus();
                }
            }
        }
        return instance;
    }


    public void register(Object obj) {
        List<SubscribleMethod> list = cacheMap.get(obj);
        if (list == null){
            list = findSubcribleMethod(obj);
            cacheMap.put(obj, list);
        }
    }

    private List<SubscribleMethod> findSubcribleMethod(Object obj) {

        List<SubscribleMethod> list = new ArrayList<>();
        Class<?> clazz = obj.getClass();
        Method[] methods = clazz.getDeclaredMethods();
        while (clazz != null) {
            //找父类时需判断是否时系统级别的父类
            String name = clazz.getName();
            if (name.startsWith("java.") || name.startsWith("javax.")
                ||name.startsWith("android.")){
                break;
            }
            for (Method method : methods) {
                // 找到带Suibscrible注解的方法
                Subscrible subscrible = method.getAnnotation(Subscrible.class);
                if (subscrible == null) {
                    continue;
                }
                //判断subscrible注解的参数类型
                Class<?>[] types = method.getParameterTypes();
                if (types.length != 1) {
                    Log.e("ERROR", "eventbus only accept one para");
                }
                ThreadMode threadMode = subscrible.threadMode();
                SubscribleMethod subscribleMethod = new SubscribleMethod(method, threadMode, types[0]);
                list.add(subscribleMethod);
            }
            clazz = clazz.getSuperclass();
        }
        return list;
    }

    public void post(final Object type) {
        //直接循环MAP里的方法，找到对应的方法回调
        Set<Object> set = cacheMap.keySet();
        Iterator<Object> iterator = set.iterator();
        while (iterator.hasNext()){
            final Object obj = iterator.next();
            List<SubscribleMethod> list = cacheMap.get(obj);
            for (final SubscribleMethod subscribleMethod : list){
                // a(if条件前面的对象)对象所对应的类信息是不是b(if条件后面的对象)对象所对应的类信息的父类或接口
                if (subscribleMethod.getType().isAssignableFrom(type.getClass())){
                    switch (subscribleMethod.getThreadMode()){
                        case MAIN:
                            //主到主
                            if (Looper.myLooper() == Looper.getMainLooper()){
                                invoke(subscribleMethod, obj, type);
                            } else { //子到主
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        invoke(subscribleMethod, obj, type);
                                    }
                                });
                            }
                            break;
                        case BACKGOROUND:
                            // Excutorsrvice 从子线程到主线程的切换
                            break;
                    }

                }
            }
        }
    }

    private void invoke(SubscribleMethod subscribleMethod, Object obj, Object type) {

        try {
            Method method = subscribleMethod.getMethod();
            method.invoke(obj, type);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }


    public synchronized void unregister(Object obj){
        List<Class<?>> subscribedTypes = (List)cacheMap.get(obj);
        if (subscribedTypes != null) {
            Iterator iterator = subscribedTypes.iterator();

            while(iterator.hasNext()) {
                Class<?> eventType = (Class)iterator.next();
                this.unsubscribeByEventType(obj, eventType);
            }

            this.cacheMap.remove(obj);
        } else {
            Log.w("ERROR", "Subscriber to unregister was not registered before: " + obj.getClass());
        }
    }

    private void unsubscribeByEventType(Object subscriber, Class<?> eventType) {
        List<SubscribleMethod> subscriptions = cacheMap.get(eventType);
        if (subscriptions != null) {
            int size = subscriptions.size();

            for(int i = 0; i < size; ++i) {
                SubscribleMethod subscription = subscriptions.get(i);
                if (subscription.getMethod().getParameterTypes() == subscriber) {
                    subscriptions.remove(i);
                    --i;
                    --size;
                }
            }
        }

    }
}
