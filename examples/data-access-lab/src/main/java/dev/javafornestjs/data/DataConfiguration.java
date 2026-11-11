package dev.javafornestjs.data;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.javafornestjs.web.TaskRepository;
import javax.sql.DataSource;
import org.apache.ibatis.annotations.Mapper;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = TaskMapper.class, annotationClass = Mapper.class)
public class DataConfiguration {
    @Bean(destroyMethod = "close")
    HikariDataSource dataSource() {
        String url = DatabaseUrl.dedicated(System.getenv("DATA_JDBC_URL"));
        var config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(required("DATA_DATABASE_USER"));
        config.setPassword(required("DATA_DATABASE_PASSWORD"));
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(500);
        config.setPoolName("data-lab");
        return new HikariDataSource(config);
    }
    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(name + " is required"); }
        return value;
    }
    @Bean
    Flyway dataMigration(DataSource source) {
        var migration = Flyway.configure().dataSource(source).locations("classpath:db/data-migration")
                .table("data_schema_history").cleanDisabled(true).load();
        migration.migrate();
        return migration;
    }
    @Bean
    SqlCounter sqlCounter() { return new SqlCounter(); }
    @Bean
    MybatisPlusInterceptor pagination() {
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
    @Bean
    @DependsOn("dataMigration")
    TaskRepository persistentTasks(TaskMapper mapper) { return new MyBatisTasks(mapper); }
}
