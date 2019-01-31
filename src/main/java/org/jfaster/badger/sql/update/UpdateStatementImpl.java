package org.jfaster.badger.sql.update;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jfaster.badger.Badger;
import org.jfaster.badger.util.CheckConditions;

/**
 *
 * @author yanpengfang
 * @create 2019-01-31 3:08 PM
 */
public class UpdateStatementImpl<T> implements UpdateStatement {

    private List<Object> paramList;

    private Badger badger;

    private String updateStatement;

    private String condition;

    private Class<T> clazz;

    public UpdateStatementImpl(Class<T> clazz, String updateStatement, String condition, Badger badger) {
        this.badger = badger;
        this.updateStatement = updateStatement;
        this.condition = condition;
        this.clazz = clazz;
    }

    @Override
    public UpdateStatement addParam(Object obj) throws Exception {
        CheckConditions.checkNotNull(obj);
        initParamList();
        paramList.add(obj);
        return this;
    }

    @Override
    public UpdateStatement addParam(Object... objs) throws Exception {
        if (objs != null && objs.length > 0) {
            initParamList();
            for (Object obj : objs) {
                CheckConditions.checkNotNull(obj);
                paramList.add(obj);
            }
        }
        return this;
    }

    @Override
    public UpdateStatement addParam(Collection<Object> objs) throws Exception {
        if (objs != null && objs.size() > 0) {
            initParamList();
            for (Object obj : objs) {
                CheckConditions.checkNotNull(obj);
                paramList.add(obj);
            }
        }
        return this;
    }

    @Override
    public int execute() throws Exception {
        return JdbcUpdateHelper.updateByCondition(clazz, updateStatement, condition, paramList, badger);
    }

    private void initParamList() {
        if (paramList == null) {
            paramList = new ArrayList<>();
        }
    }
}
