package com.xq.action;

public abstract class Action {

    private ActionManager actionManager;

    public Action(ActionManager actionManager) {
        this.actionManager = actionManager;
    }

    public abstract void init();

    public abstract void release();

    private String name;
    protected String getName(){
        if (name == null){
            name = getClass().getAnnotation(ActionName.class).name();
        }
        return name;
    }

    private String[] abilities;
    protected String[] getAbilities(){
        if (abilities == null){
            abilities = getClass().getAnnotation(ActionAbility.class).abilities();
        }
        return abilities;
    }

    public ActionManager getActionManager(){
        return actionManager;
    }

}
