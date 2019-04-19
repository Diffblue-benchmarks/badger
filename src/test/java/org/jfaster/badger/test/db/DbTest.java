package org.jfaster.badger.test.db;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jfaster.badger.Badger;
import org.jfaster.badger.exception.MappingException;
import org.jfaster.badger.sql.delete.DeleteStatement;
import org.jfaster.badger.sql.interceptor.SqlInterceptor;
import org.jfaster.badger.sql.select.Condition;
import org.jfaster.badger.sql.select.Query;
import org.jfaster.badger.sql.select.SQLQuery;
import org.jfaster.badger.sql.update.UpdateSqlStatement;
import org.jfaster.badger.sql.update.UpdateStatement;
import org.jfaster.badger.test.entity.Driver;
import org.jfaster.badger.test.entity.DriverExt;
import org.jfaster.badger.test.entity.DriverOrder;
import org.jfaster.badger.test.entity.Order;
import org.jfaster.badger.test.entity.TypeEnum;
import org.jfaster.badger.transaction.TransactionTemplate;
import org.junit.BeforeClass;
import org.junit.Test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 *
 * @author yanpengfang
 * @create 2019-01-31 9:56 PM
 */
public class DbTest {

    private static Badger badger;

    /**
     * 构建Badger实例
     */
    @BeforeClass
    public static void constructBadger() {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("com.mysql.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/store");
        config.setUsername("yanpengfang");
        config.setPassword("272777475");
        config.setMaximumPoolSize(10);

        HikariDataSource dataSource = new HikariDataSource(config);

        badger = Badger.newInstance(dataSource);
        badger.setInterceptor(new SqlInterceptor() {

            @Override
            public void before(String sql) {
                System.out.println("sql:" + sql + " begin to execute");
                SqlInterceptor.put("startTime", System.currentTimeMillis());
            }

            @Override
            public void after(String sql) {
                long startTime = SqlInterceptor.get("startTime", Long.class);
                System.out.println("sql:" + sql + " execute success, execute time:" + (System.currentTimeMillis() - startTime));
            }

            @Override
            public void error(String sql, Throwable e) {
                System.out.println("sql:" + sql + " execute fail " + e.getMessage());
            }
        });
    }

    /**
     * 插入，插入所有字段，插入非空字段，插入唯一索引冲突忽略。
     */
    @Test
    public void insertTest() {
        Date now = new Date();
        Driver driver = new Driver();
        driver.setAge(43);
        driver.setDriverName("王叼蛋");
        driver.setType(TypeEnum.JOIN);
        driver.setCreateDate(now);

        //保存非空字段并且回填自增主键id
        badger.saveNotNull(driver);

        driver = new Driver();
        driver.setAge(43);
        driver.setDriverName("刘叼蛋");
        driver.setType(TypeEnum.SELF);
        driver.setCreateDate(now);
        driver.setUpdateDate(now);
        //保存数据并且回填自增主键id
        badger.save(driver);
        System.out.println("司机ID:" + driver.getDriverId());

        Order order = new Order();

        order.setOrderNo("P22437896" + System.currentTimeMillis());
        System.out.println("订单号:" + order.getOrderNo());
        order.setDriverId(driver.getDriverId());
        order.setMoney(new BigDecimal("189.02"));
        order.setUpdateDate(now);
        order.setCreateDate(now);
        //忽略唯一索引冲突
        badger.saveIgnore(order);
    }

    /**
     * 插入 手动指定分库分表字段
     */
    @Test
    public void insertManualShardTest() {
        Date now = new Date();
        Driver driver = new Driver();
        driver.setAge(47);
        driver.setDriverName("指定分表");
        driver.setType(TypeEnum.SELF);
        driver.setCreateDate(now);
        driver.setUpdateDate(now);
        //保存数据并且回填自增主键id
        badger.save(driver);
        System.out.println("司机ID:" + driver.getDriverId());

        Order order = new Order();

        order.setOrderNo("P22437896" + System.currentTimeMillis());
        System.out.println("订单号:" + order.getOrderNo());
        order.setDriverId(driver.getDriverId());
        order.setMoney(new BigDecimal("189.02"));
        order.setUpdateDate(now);
        order.setCreateDate(now);
        //忽略唯一索引冲突, 并且指定分库分表的值，会覆盖系统提取的值
        badger.saveIgnore(order, 11);
    }

    /**
     *  根据id删除。
     */
    @Test(expected = MappingException.class)
    public void deleteTest() {
        badger.delete(Driver.class, 17);
        //分库分表字段不是id则抛异常
        badger.delete(Order.class, "P224378961549863778117");
    }

    /**
     * 根据id更新所有字段。
     */
    @Test
    public void updateTest() {
        Driver driver = badger.get(Driver.class, 19);
        if (driver == null || driver.getDriverId() == 0) {
            return;
        }
        driver.setAge(53);
        driver.setType(TypeEnum.SELF);
        badger.update(driver);
    }

    /**
     * 根据条件删除
     */
    @Test
    public void deleteByConditionTest() {
        DeleteStatement statement = badger.createDeleteStatement(Driver.class, "type=? and age=?");
        statement.addParam(TypeEnum.JOIN)
                .addParam(43)
                .execute();
    }

    /**
     * 根据条件更新指定字段。
     */
    @Test
    public void updateByConditionTest() {
        UpdateStatement statement = badger.createUpdateStatement(Order.class,
                "money=?, update_date=?", "order_no=? and driver_id=?");
        statement.addParam(new BigDecimal("126"))
                .addParam(new Date())
                .addParam("P224378961549892939886")
                .addParam(15) //根据driver_id分表必须带分表字段
                .execute();
    }

    /**
     * 根据条件查询，查询指定字段。
     */
    @Test
    public void selectByConditionTest() {
        //根据条件查询所有字段
        Query<Driver> query = badger.createQuery(Driver.class, "driver_id >=1 and driver_id <= ?");
        query.addParam(30);
        List<Driver> drivers = query.list();
        System.out.println(drivers);

        //根据条件查询指定字段
        Query<Order> queryOrder = badger.createQuery(Order.class, "order_no,money", "driver_id = ?");
        queryOrder.addParam(13); //根据driver_id分表必须带分表字段
        List<Order> orders = queryOrder.list();
        System.out.println(orders);

        //like查询
        Query<Driver> queryLike = badger.createQuery(Driver.class, "driver_name like ?");
        queryLike.addParam("%叼蛋%");
        drivers = queryLike.list();
        System.out.println(drivers);

        //in 查询
        Query<Driver> queryIn = badger.createQuery(Driver.class, "driver_id in (?,?,?)");
        queryIn.addParam(17).addParam(19).addParam(20);
        drivers = queryIn.list();
        System.out.println(drivers);


    }

    @Test
    public void selectByLogicConditionTest() {
        //根据条件查询所有字段
        Condition condition = badger.createCondition()
                .gte("driver_id", 1)
                .and()
                .lte("driver_id", 30);
        List<Driver> drivers = badger.createQuery(Driver.class, condition).list();
        System.out.println(drivers);

        List<Integer> driverIds = new ArrayList<>();
        driverIds.add(1);
        driverIds.add(10);
        driverIds.add(18);
        condition = badger.createCondition().in("driver_id", driverIds);
        badger.createDeleteStatement(Driver.class, condition).execute();
        //in 查询
        List<Driver> drivers1 = badger.createQuery(Driver.class, condition).list();
        System.out.println(drivers1);

        Condition condition1 = badger.createCondition()
                .eq("order_no", "P224378961549892939886")
                .and()
                .eq("driver_id", 15);
        badger.createUpdateStatement(Order.class,
                "money=?, update_date=?", condition1.getSql())
                .addParam(new BigDecimal("126"))
                .addParam(new Date())
                .addParam(condition1.getParams())
                .execute();
    }

    /**
     * 根据条件查询，查询指定字段。
     */
    @Test
    public void selectManualShardByConditionTest() {
        //根据条件查询所有字段
        Query<Order> query = badger.createQuery(Order.class, "order_no=?");
        //指定分库分表字段，如果不指定查询条件必须带有driverId，因为是按照driverId分库分表，手动指定会覆盖程序提取。
        Order order = query.addParam("P224378961552032130141").setShardValue(11).getOne();
        System.out.println(order);
    }

    /**
     * 分页查询
     */
    @Test
    public void selectByPageTest() {
        Query<Driver> query = badger.createQuery(Driver.class, "create_date >= ? and create_date <= ?");
        Date now = new Date();
        Date before = new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(10));
        query.addParam(before);
        query.addParam(now);

        //一共多少条
        long count = query.count();
        System.out.println("总条数:" + count);

        query.setPageIndex(0);
        query.setPageSize(10);

        List<Driver> drivers = query.list();
        System.out.println(drivers);

        Query<Order> queryDriverOrder = badger.createQuery(Order.class, "driver_id=?").addParam(2).setPageIndex(0).setPageSize(10);
        System.out.println(queryDriverOrder.count());
    }

    /**
     * 指定查询返回的类型
     */
    @Test
    public void selectType() {
        Query<Integer> query = badger.createQuery(Driver.class, Integer.class, "avg(age)", "1=1 group by driver_id");
        Integer avg = query.getOne();
        System.out.println(avg);
    }

    /**
     * 指定查询返回的类型
     */
    @Test
    public void selectBeanType() {
        Query<DriverExt> query = badger.createQuery(Driver.class, DriverExt.class, "avg(age) as avgAge, driver_id", "1=1 group by driver_id");
        List<DriverExt> avg = query.list();
        System.out.println(avg);
    }

    /**
     * 扩展表结构
     */
    @Test
    public void selectExt() {
        Query<DriverExt> query = badger.createQuery(DriverExt.class, "avg(age) as avgAge, driver_id", "1=1 group by driver_id");
        List<DriverExt> driverExts = query.list();
        System.out.println(driverExts);
    }

    /**
     * 自定义sql更新，不支持分库分表，分库分表信息得自己实现
     */
    @Test
    public void updateBySelfDefine() {
        UpdateSqlStatement sqlStatement = badger.createUpdateSqlStatement("update driver set update_date=? where driver_id=?");
        sqlStatement.addParam(new Date());
        sqlStatement.addParam(14);
        sqlStatement.execute();
    }

    /**
     * 自定义sql查询，不支持分库分表，分库分表信息得自己实现，复杂sql查询可以拆分成多次单表查询。大表本身不建议join
     */
    @Test
    public void selectBySelfDefine() {
        SQLQuery<DriverOrder> query = badger.createSqlQuery(DriverOrder.class, "select a.driver_id,b.order_no from driver a join driver_order_1 b on a.driver_id=b.driver_id where a.driver_id=?");
        query.addParam(13);
        List<DriverOrder> driverOrders = query.list();
        System.out.println(driverOrders);
    }

    /**
     * badger事务
     */
    @Test(expected = MappingException.class)
    public void transactionTest() {
        TransactionTemplate.execute(status -> {
            badger.delete(Driver.class, 15);
            //根据id删除，由于分库分表字段不是id则抛异常
            badger.delete(Order.class, "P224378961549867525895");
        });
    }
}
