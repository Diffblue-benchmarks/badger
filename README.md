# Badger

# 概要

Badger轻量级单表操作dao框架，提供分库分表，类型映射等功能。

# 例子

> 例子中使用了lombok，简化bean。

## 添加依赖

```xml
<dependency>
    <groupId>org.jfaster</groupId>
    <artifactId>badger</artifactId>
    <version>1.5</version>
</dependency>
```



## 实例化，以使用myql和HikariCp连接池为例

```java
HikariConfig config = new HikariConfig();
config.setDriverClassName("com.mysql.jdbc.Driver");
config.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/store");
config.setUsername("yanpengfang");
config.setPassword("8888888888");
config.setMaximumPoolSize(10);

HikariDataSource dataSource = new HikariDataSource(config);
Badger badger = Badger.newInstance(dataSource);
```

## 定义实体类

```java
public enum TypeEnum {

    SELF("自营"), JOIN("加盟");

    @Getter
    private String desc;

    TypeEnum(String desc) {
        this.desc = desc;
    }
}
```

```java
@Data
@Table(tableName = "driver")
public class Driver {

    @Id
    @Column
    private int driverId;

    @Column
    private String driverName;

    @Column
    private int age;

    @Column(convert = EnumIntegerConverter.class)
    private TypeEnum type;

    @Column
    private Date createDate;

    @Column
    private Date updateDate;

}
```
> Table注解有tableName属性如果不指定默认是类的名转下划线，dataSourceName用于指定数据源工厂名称，如果是单库不用指定，如果是多库需要指定，指定注册到badger里的数据源工厂名称即可，见下文。
> Column注解的name属性可以指定与数据库字段对应关系，如果不写默认是驼峰转下划线，convert属性是将bean的类型映射成数据库的类型，如果需要可以实现TypeConverter接口自行定义，默认提供EnumIntegerConverter、EnumStringConverter、FastJsonObjectConverter、IntArrayStringConverter、IntegerListStringConverter、IntegerSetStringConverter、StringArrayStringConverter、StringListStringConverter、StringSetStringConverter等。

## 插入

```java
/**
 * 插入，插入所有字段，插入非空字段。
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
    
    //忽略唯一索引冲突
    badger.saveIgnore(driver);
    System.out.println("司机ID:" + driver.getDriverId());
}

```

> badger还提供了saveIgnore方法，用于忽略唯一索引冲突

## 删除

### 根据id删除

```java
/**
 *  根据id删除。
 */
@Test(expected = MappingException.class)
public void deleteTest() throws Exception {
    badger.delete(Driver.class, 3);
}
```

### 根据条件删除

```java
/**
 * 根据条件删除
 */
@Test
public void deleteByConditionTest() {
    DeleteStatement statement = badger.createDeteleStatement(Driver.class, 
                                                             "type=? and age=?");
    statement.addParam(TypeEnum.JOIN)
            .addParam(43)
            .execute();
}
```

## 修改

### 根据id修改所有字段

```java
/**
 * 根据id更新所有字段
 */
@Test
public void updateTest() {
    Driver driver = badger.get(Driver.class, 14);
    if (driver == null || driver.getDriverId() == 0) {
        return;
    }
    driver.setAge(53);
    driver.setType(TypeEnum.SELF);
    badger.update(driver);
}
```

### 根据条件修改指定字段

```java
/**
 * 根据条件更新指定字段。
 */
@Test
public void updateByConditionTest() {
    UpdateStatement statement = badger.createUpdateStatement(Order.class,
                                                             "age=?, update_date=?",
                                                             "type=? and driver_id=?");
    statement.addParam(54)
            .addParam(new Date())
            .addParam(TypeEnum.SELF)
            .addParam(13) //根据driver_id
            .execute();
}
```

## 查询

### 根据id查询所有字段

```java
@Test
public void selectTest() {
    Driver driver = badger.get(Driver.class, 14);
    System.out.println(driver);
}
```

### 根据条件查询所有字段

```java
/**
 * 根据条件查询所有字段。
 */
@Test
public void selectAllByConditionTest() {
    //根据条件查询所有字段
    Query<Driver> query = badger.createQuery(Driver.class, 
                                             "driver_id >=1 and driver_id <= ?");
    List<Driver> drivers =  query.addParam(14).list();
    System.out.println(drivers);
}
```

### 根据条件查询指定字段

```java
/**
 * 根据条件查询指定字段。
 */
@Test
public void selectColumnsByConditionTest() {
    //根据条件查询指定字段
    Query<Driver> queryDriver = badger.createQuery(Driver.class, 
                                                   "age,type", 
                                                   "type = ? and create_date < ?");
    List<Driver> drivers = queryDriver.addParam(TypeEnum.SELF)
               .addParam(new Date())
               .list();
    System.out.println(drivers);
}
```

### like和in查询

```java
@Test
public void selectByConditionTest() {
    //like查询
    Query<Driver> queryLike = badger.createQuery(Driver.class, "driver_name like ?");
    List<Driver> drivers = queryLike.addParam("%叼蛋%").list();
    System.out.println(drivers);

    //in 查询
    Query<Driver> queryIn = badger.createQuery(Driver.class, "driver_id in (?,?,?)");
    drivers = queryIn.addParam(17)
                     .addParam(19)
                     .addParam(20)
                     .list();
    System.out.println(drivers);
}
```

### 分页查询

```java
/**
 * 分页查询
 */
@Test
public void selectByPageTest() {
    Query<Driver> query = badger.createQuery(Driver.class, 
                                             "create_date >= ? and create_date <= ?");
    Date now = new Date();
    Date before = new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(10));
    query.addParam(before)
          .addParam(now);

    //一共多少条
    long count = query.count();
    System.out.println("总条数:" + count);

    List<Driver> drivers = query.setPageIndex(0)
                                .setPageSize(10)
                                .list();
    System.out.println(drivers);
}
```

### 指定查询返回的类型

> 指定查询类型，允许自定义返回值的类型，例如返回单值等，也支持bean类型。

```java

/**
 * 指定查询返回的类型
 */
@Test
public void selectType() {
  Query<Integer> query = badger.createQuery(Driver.class, Integer.class,"avg(age)", "1=1 group by driver_id");
  Integer avg = query.getOne();
  System.out.println(avg);
}

@Data
public class DriverExt {

    @Column(name = "avgAge")
    int avgAge;

    @Column
    int driverId;
}

/**
  * 指定查询返回的类型
  */
@Test
public void selectBeanType() {
  Query<DriverExt> query = badger.createQuery(Driver.class, DriverExt.class,"avg(age) as avgAge, driver_id", "1=1 group by driver_id");
  List<DriverExt> avg = query.list();
  System.out.println(avg);
}
```

### 扩展查询

> 根据其他类定义好的表结构，进行一些操作，比如sum，min等等产生的字段，这些字段并不属于原来的表，但是是根据原来的表生成的。也可以通过上一节讲的，指定返回值类型实现。

```java
@Data
@Extension(extend = Driver.class)
public class DriverExt {

    @Column(name = "avgAge")
    int avgAge;

    @Column
    int driverId;
}
```

```java
Query<DriverExt> query = badger.createQuery(DriverExt.class, 
                                            "avg(age) as avgAge, driver_id", 
                                            "1=1 group by driver_id");
List<DriverExt> driverExts = query.list();
System.out.println(driverExts);
```

## 分库分表

> 根据某个字段进行分库分表。如果根据某个字段进行分库分表则所有的操作必须带有分库分表字段。目前只支持单值分库分表，只要在分库分表的属性上打上@ShardColumn
即可，框架会自动提取值调用指定的分库分表算法。

```java
/**
 * 分表分库
 * @author yanpengfang
 * @create 2019-01-31 9:41 PM
 */
@ShardTable(tables = {"driver_order_0", "driver_order_1"},
        tableShardStrategy = Order.OrderTableShardStrategy.class
        /*,dbShardStrategy = Order.OrderDataSourceShardStrategy.class*/)
@Data
public class Order {

    @Id
    @Column
    private String orderNo;

    @Column
    private BigDecimal money;

    @ShardColumn
    @Column
    private int driverId;

    @Column
    private Date createDate;

    @Column
    private Date updateDate;

    public static class OrderTableShardStrategy implements TableShardStrategy<Integer> {
        @Override
        public String shardSingle(List<String> tables, Integer shardValue) {
            int mod = shardValue % 2;
            for (String table : tables) {
                if (table.endsWith("_" + mod)) {
                    return table;
                }
            }
            return tables.get(0);
        }
    }

    /*public static class OrderDataSourceShardStrategy implements DataSourceShardStrategy<Integer> {
        @Override
        public String shardSingle(Integer shardValue) {
            int mod = shardValue % 2;
            return "db_" + mod;
        }
    }*/
}

```

### 分表操作

> 以插入为例，其他的操作和不分库分表的操作一样。

```java
/**
 * 插入唯一索引冲突忽略。
 */
@Test
public void insertTest() {
    Order order = new Order();
    order.setOrderNo("P22437896" + System.currentTimeMillis());
    System.out.println("订单号:" + order.getOrderNo());
    //根据driver_id分表，则按照例子的算法应该分到driver_order_1表
    order.setDriverId(13);
    order.setMoney(new BigDecimal("189.02"));
    order.setUpdateDate(now);
    order.setCreateDate(now);
    //忽略唯一索引冲突
    badger.saveIgnore(order);
}
```

### 分库操作

> 按照如下配置Badger，然后将Order中的注释打开，就可以实现分库了。分库和分表可以同时使用，也可以只使用其中之一。

```java
HikariConfig config = new HikariConfig();
config.setDriverClassName("com.mysql.jdbc.Driver");
config.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/store");
config.setUsername("yanpengfang");
config.setPassword("88888888");
config.setMaximumPoolSize(10);

HikariDataSource dataSource = new HikariDataSource(config);

HikariConfig config1 = new HikariConfig();
config1.setDriverClassName("com.mysql.jdbc.Driver");
config1.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/store1");
config1.setUsername("yanpengfang");
config1.setPassword("8888888");
config1.setMaximumPoolSize(10);

HikariDataSource dataSource1 = new HikariDataSource(config);

Badger badger = Badger.newInstance();
//指定数据源工厂名称为db_0
badger.setDataSourceFactory(new SingleDataSourceFactory("db_0", dataSource));
//指定数据源工厂名称为db_1
badger.setDataSourceFactory(new SingleDataSourceFactory("db_1", dataSource1));
```
### 手动指定分库分表

> 如果使用了分库分表功能，框架会自动根据@ShardColumn获取分库分表的值，则任何db操作都必须带上分库分表字段，但是存在一些特殊情况，比如分了10张表，有一种需求是遍历这10张表，那么这样查询条件就比较麻烦了，badger支持手动指定分库分表的值，手动指定会覆盖框架提取的值。

#### 增加

```java
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
    //忽略唯一索引冲突, 并且指定分库分表的值为11，会覆盖系统提取的值。
    badger.saveIgnore(order, 11);
}
```

> 只需要在保存的时候指定一个分库分表的值。同理根据id更新、查找和删除，只需要在方法里传入指定的分库分表值即可。

#### 查询

```java
/**
 * 根据条件查询，查询指定字段。
 */
@Test
public void selectManualShardByConditionTest() {
    //根据条件查询所有字段
    Query<Order> query = badger.createQuery(Order.class, "order_no=?");
    //指定分库分表字段的值为11，如果不指定则查询条件必须带有driverId，因为是按照driverId分库分表，手动指定会覆盖程序提取。
    Order order = query.addParam("P224378961552032130141")
                        .setShardValue(11)
                        .getOne();
    System.out.println(order);
}
```

> 只需要在查询的时候调用setShardValue方法指定分库分表的值即可，根据条件删除、修改也是一样。

### 读写分离

> 指定MasterSlaveDataSourceFactory数据源工厂，则查询语句自动走从库。

```java
HikariConfig config = new HikariConfig();
config.setDriverClassName("com.mysql.jdbc.Driver");
config.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/store");
config.setUsername("yanpengfang");
config.setPassword("8888888");
config.setMaximumPoolSize(10);

HikariDataSource dataSource = new HikariDataSource(config);

HikariConfig config1 = new HikariConfig();
config1.setDriverClassName("com.mysql.jdbc.Driver");
config1.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/store1");
config1.setUsername("yanpengfang");
config1.setPassword("88888888");
config1.setMaximumPoolSize(10);

HikariDataSource dataSource1 = new HikariDataSource(config);

Badger badger = Badger.newInstance();
badger.setDataSourceFactory(new MasterSlaveDataSourceFactory(dataSource,Collections.singletonList(dataSource1)));

```

#### 读写分离，查询强制走主库

```java
/**
 * 根据id更新所有字段。
 */
@Test
public void updateTest() {
    Driver driver = badger.get(Driver.class, 14, true);
    System.out.println(driver);
}
```

```java
/**
 * 根据条件查询，查询指定字段。
 */
@Test
public void selectByConditionTest() {
    //根据条件查询所有字段
    Query<Driver> query = badger.createQuery(Driver.class, 
                                             "driver_id >=1 and driver_id <= ?");
    query.addParam(14);
    List<Driver> drivers = query.userMaster().list();
    System.out.println(drivers);
}

```

## 事务
> Badger既实现了自己的事务管理，同时也支持spring的事务管理器，支持spring的事务传播机制。使用其中一种即可。
### Badger自身事务

> 实现TransactionAction方法，在这个方法中把要执行的一系列事务操作放在其中。

```java
/**
 * badger事务
 */
@Test(expected = MappingException.class)
public void transactionTest() {
    TransactionTemplate.execute(status -> {
        badger.delete(Driver.class, 15);
        //根据id删除，但是分库分表字段不是id则抛异常
        badger.delete(Order.class, "P224378961549867525895");
    });
}
```

### 使用spring提供的事务管理器

> 使用spring的事务管理器，同时支持spring的事务传递机制。

### 添加依赖

```xml
<!--spring事务管理器-->
<dependency>
    <groupId>org.jfaster</groupId>
    <artifactId>badger-spring-transaction</artifactId>
    <version>1.5</version>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-jdbc</artifactId>
    <version>5.1.4.RELEASE</version>
</dependency>
<dependency>
    <groupId>org.jfaster</groupId>
    <artifactId>badger</artifactId>
    <version>1.5</version>
</dependency>
```



#### 配置xml文件

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xmlns:tx="http://www.springframework.org/schema/tx"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
                           http://www.springframework.org/schema/beans/spring-beans.xsd
                           http://www.springframework.org/schema/context
                           http://www.springframework.org/schema/context/spring-context.xsd
                           http://www.springframework.org/schema/tx
                           http://www.springframework.org/schema/tx/spring-tx.xsd"
>

    <context:component-scan base-package="org.jfaster.test"/>

    <!-- 加载db配置文件 -->
    <context:property-placeholder location="classpath*:*.properties" ignore-unresolvable="true"/>

    <bean id="hikariConfig" class="com.zaxxer.hikari.HikariConfig">
        <!--poolName属性自定义即可-->
        <property name="poolName" value="springHikariCP"/>
        <property name="driverClassName" value="${jdbc.driver}"/>
        <property name="maximumPoolSize" value="${jdbc.maximumPoolSize}"/>
        <property name="jdbcUrl" value="${jdbc.url}"/>
        <property name="username" value="${jdbc.username}"/>
        <property name="password" value="${jdbc.password}"/>
    </bean>

    <!-- HikariCP dataSource配置 -->
    <bean id="hikaricpDataSource" class="com.zaxxer.hikari.HikariDataSource" destroy-method="close">
        <constructor-arg ref="hikariConfig"/>
    </bean>


    <!-- 注册事务管理类 -->
    <bean id="transactionManager"
          class="org.springframework.jdbc.datasource.DataSourceTransactionManager">
        <property name="dataSource" ref="hikaricpDataSource"/>
    </bean>

    <!-- 开启事务行为 -->
    <tx:annotation-driven/>

    <!--初始化badger-->
    <bean id="badger" class="org.jfaster.badger.Badger" factory-method="newInstance">
        <property name="dataSource" ref="hikaricpDataSource"/>
        <!--设置事务管理器为spring-->
        <property name="transactionManager" value="spring"/>
    </bean>

</beans>
```

### 定义dao

```java
/**
 *
 * @author yanpengfang
 * @create 2019-02-13 10:27 AM
 */
@Repository
public class DriverDao {

    @Resource
    private Badger badger;

    public int deleteDriverById(int driverId) {
        return badger.delete(Driver.class, driverId);
    }

}
```

```java
/**
 *
 * @author yanpengfang
 * @create 2019-02-13 10:27 AM
 */
@Repository
public class DriverOrderDao {

    @Resource
    private Badger badger;

    public int deleteDriverOrderByOrderNo(String orderNo) {
        return badger.delete(Order.class, orderNo);
    }

}
```

#### 定义service

```java
/**
 *
 * @author yanpengfang
 * @create 2019-02-13 10:37 AM
 */
public interface DriverOrderService {
    boolean deleteDriverAndOrder(int driverId, String orderNo);
}
```

#### 定义service的实现

> 打上@Transactional注解

```java
/**
 *
 * @author yanpengfang
 * @create 2019-02-13 10:37 AM
 */
@Service
public class DriverOrderServiceImpl implements DriverOrderService {

    @Resource
    private DriverDao driverDao;

    @Resource
    private DriverOrderDao driverOrderDao;

    @Override
    @Transactional
    public boolean deleteDriverAndOrder(int driverId, String orderNo) {
        return driverDao.deleteDriverById(driverId) > 0
                && driverOrderDao.deleteDriverOrderByOrderNo(orderNo) > 0;
    }
}

```

## 设置拦截器

> 设置拦截器，可以在sql执行之前和之后做一些操作，比如统计sql的执行时间等等

```java
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
```

## 自定义sql

> 自定义sql，不建议使用。脱离了单表操作，根据个人需要自定义sql。在自定义sql情况下，Badger不解析sql，所以不会重写表名，不支持分库分表。分表的信息需要自己实现，拼接到sql中。分库操作则需要指定库名。复杂sql查询可以拆分成多次单表查询。大表本身不建议join等操作。

### 查询

```java
/**
 *
 * @author yanpengfang
 * @create 2019-02-11 4:00 PM
 */
@Data
public class DriverOrder {

    private int driverId;

    private String orderNo;
}
```



```java
@Test
public void selectBySelfDefine() {
    SQLQuery<DriverOrder> query = badger.createSqlQuery(DriverOrder.class, "select a.driver_id,b.order_no from driver a join driver_order_1 b on a.driver_id=b.driver_id where a.driver_id=?");
    query.addParam(13);
    //query.setDataSourceName("xx"); 如果分库需要自己指定。
    List<DriverOrder> driverOrders = query.list();
    System.out.println(driverOrders);
}
```

### 更新

```java
/**
 * 自定义sql更新，不支持分库分表，分库分表信息得自己实现
 */
@Test
public void updateBySelfDefine() {
    UpdateSqlStatement sqlStatement = badger.createUpdateSqlStatement("update driver set update_date=? where driver_id=?");
    //sqlStatement.setDataSourceName("xx");如果分库需要自己指定。
    sqlStatement.addParam(new Date());
    sqlStatement.addParam(14);
    sqlStatement.execute();
}
```

