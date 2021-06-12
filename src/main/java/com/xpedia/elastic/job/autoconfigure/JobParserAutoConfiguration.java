package com.xpedia.elastic.job.autoconfigure;

import com.dangdang.ddframe.job.lite.lifecycle.api.JobAPIFactory;
import com.dangdang.ddframe.job.lite.lifecycle.api.JobOperateAPI;
import com.dangdang.ddframe.job.reg.zookeeper.ZookeeperConfiguration;
import com.dangdang.ddframe.job.reg.zookeeper.ZookeeperRegistryCenter;
import com.xpedia.elastic.job.parser.JobConfParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.common.base.Optional;

import java.util.HashSet;

/**
 * ElasticJob 依赖Bean自动配置
 *
 * @author Xpedia
 */
@Configuration
@EnableConfigurationProperties(ZookeeperProperties.class)
public class JobParserAutoConfiguration {
    /**
     * ZK配置
     */
    @Autowired
    private ZookeeperProperties zookeeperProperties;
    /**
     * elastic-job 当前机器执行job列表
     * <p>
     * 用于多机房任务，分布不同时，初始化调度
     * </p>
     */
    @Value("${elastic.job.execute.list}")
    private String currentServerExecuteJobList;
    /**
     * elastic-job 任务是否全部执行
     * 改值设置为true时，将忽略
     * elastic.job.execute.list
     * 中的配置，执行所有带有EsJobConf的任务
     */
    @Value("${elastic.job.execute.all}")
    private boolean executeAll;

    /**
     * ZK注册中心
     */
    @Bean(initMethod = "init", name = "elasticJobZkCenter")
    public ZookeeperRegistryCenter zookeeperRegistryCenter() {
        ZookeeperConfiguration zkConfig = new ZookeeperConfiguration(zookeeperProperties.getServerLists(),
                zookeeperProperties.getNamespace());
        zkConfig.setBaseSleepTimeMilliseconds(zookeeperProperties.getBaseSleepTimeMilliseconds());
        zkConfig.setConnectionTimeoutMilliseconds(zookeeperProperties.getConnectionTimeoutMilliseconds());
        zkConfig.setDigest(zookeeperProperties.getDigest());
        zkConfig.setMaxRetries(zookeeperProperties.getMaxRetries());
        zkConfig.setMaxSleepTimeMilliseconds(zookeeperProperties.getMaxSleepTimeMilliseconds());
        zkConfig.setSessionTimeoutMilliseconds(zookeeperProperties.getSessionTimeoutMilliseconds());
        return new ZookeeperRegistryCenter(zkConfig);
    }

    /**
     * JobParser Bean
     * <p>
     * 可使用该bean中的modifyCurrentServerExecuteJobSet（executeall，targetJobSet）方法
     * 动态更新当前服务器执行列表
     * </p>
     */
    @Bean
    public JobConfParser jobConfParser(@Qualifier("jobOperateApi") JobOperateAPI joboperateapi,
                                       @Qualifier("elasticJobZkCenter") ZookeeperRegistryCenter elasticJobZkCenter) {

        return new JobConfParser(elasticJobZkCenter, new HashSet<>(), joboperateapi, executeAll);
    }

    @Bean(name = "jobOperateApi")
    @SuppressWarnings("all")
    public JobOperateAPI joboperateapi() {
        return JobAPIFactory.createJobOperateAPI(zookeeperProperties.getServerLists(), zookeeperProperties.getNamespace(),
                Optional.fromNullable(zookeeperProperties.getDigest()));
    }

}
