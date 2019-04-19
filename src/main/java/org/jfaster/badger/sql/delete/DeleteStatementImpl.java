package org.jfaster.badger.sql.delete;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jfaster.badger.Badger;
import org.jfaster.badger.sql.select.Condition;
import org.jfaster.badger.util.CheckConditions;

public class DeleteStatementImpl implements DeleteStatement {
    private Class<?> clazz;
    private String condition;
    private List<Object> paramList = null;
    private Object shardValue;
    private Badger badger;

    private Condition logicCondition;

    public DeleteStatementImpl(Class<?> clazz, String condition, Badger badger) {
        this.clazz = clazz;
        this.condition = condition;
        this.badger = badger;
    }

    public DeleteStatementImpl(Class<?> clazz, Condition condition, Badger badger) {
        this.clazz = clazz;
        this.logicCondition = condition;
        this.badger = badger;
    }

    private void initParamList() {
        if (paramList == null) {
            paramList = new ArrayList<>();
        }
    }

    @Override
    public DeleteStatement addParam(Object obj) {
        if (logicCondition != null) {
            return this;
        }
        CheckConditions.checkNotNull(obj, "parameter can not be null");
        initParamList();
        paramList.add(obj);
        return this;
    }

    @Override
    public DeleteStatement addParamIfNotNull(Object obj) {
        if (logicCondition != null) {
            return this;
        }
        if (obj != null) {
            initParamList();
            paramList.add(obj);
        }
        return this;
    }

    @Override
    public DeleteStatement addParam(Object... objs) {
        if (logicCondition != null) {
            return this;
        }
        if (objs != null && objs.length > 0) {
            initParamList();
            for (Object obj : objs) {
                CheckConditions.checkNotNull(obj, "parameter can not be null");
                paramList.add(obj);
            }
        }
        return this;
    }

    @Override
    public DeleteStatement addParam(Collection<Object> objs) {
        if (logicCondition != null) {
            return this;
        }
        if (objs != null && objs.size() > 0) {
            initParamList();
            for (Object obj : objs) {
                CheckConditions.checkNotNull(obj, "parameter can not be null");
                paramList.add(obj);
            }
        }
        return this;
    }

    @Override
    public DeleteStatement setShardValue(Object shardValue) {
        this.shardValue = shardValue;
        return this;
    }

    @Override
    public int execute() {
        if (logicCondition != null) {
            paramList = logicCondition.getParams();
            condition = logicCondition.getSql();
        }
        if (shardValue != null) {
            return JdbcDeleteHelper.deleteByCondition(clazz, condition, paramList, shardValue, badger);
        }
        return JdbcDeleteHelper.deleteByCondition(clazz, condition, paramList, badger);
    }
}
