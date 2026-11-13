package com.enjoytrip;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.*;
import java.sql.Connection;
@Intercepts(@Signature(type=StatementHandler.class,method="prepare",args={Connection.class,Integer.class}))
public class SqlStatementCounter implements Interceptor {
 private final java.util.concurrent.atomic.AtomicLong count=new java.util.concurrent.atomic.AtomicLong();
 public void clear() { count.set(0); }
 public long count() { return count.get(); }
 @Override public Object intercept(Invocation invocation) throws Throwable { count.incrementAndGet(); return invocation.proceed(); }
}
