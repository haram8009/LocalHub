package com.enjoytrip;

import java.util.Properties;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.springframework.context.annotation.*;

/** DB 종류별 SQL(databaseId="mysql" | "h2")을 고르기 위한 설정. databaseId 가 없는 SQL은 모든 DB에서 쓰입니다. */
@Configuration
public class MyBatisConfig {
    @Bean DatabaseIdProvider databaseIdProvider() {
        var vendors = new Properties();
        vendors.setProperty("MySQL", "mysql");
        vendors.setProperty("H2", "h2");
        var provider = new VendorDatabaseIdProvider();
        provider.setProperties(vendors);
        return provider;
    }
}
