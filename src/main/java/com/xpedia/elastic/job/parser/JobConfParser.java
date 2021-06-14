package com.xpedia.elastic.job.parser;

import com.dangdang.ddframe.job.api.ElasticJob;
import com.dangdang.ddframe.job.api.dataflow.DataflowJob;
import com.dangdang.ddframe.job.api.script.ScriptJob;
import com.dangdang.ddframe.job.api.simple.SimpleJob;
import com.dangdang.ddframe.job.config.JobCoreConfiguration;
import com.dangdang.ddframe.job.config.JobTypeConfiguration;
import com.dangdang.ddframe.job.config.dataflow.DataflowJobConfiguration;
import com.dangdang.ddframe.job.config.script.ScriptJobConfiguration;
import com.dangdang.ddframe.job.config.simple.SimpleJobConfiguration;
import com.dangdang.ddframe.job.lite.api.strategy.impl.AverageAllocationJobShardingStrategy;
import com.dangdang.ddframe.job.lite.config.LiteJobConfiguration;
import com.dangdang.ddframe.job.lite.lifecycle.api.JobOperateAPI;
import com.dangdang.ddframe.job.lite.spring.api.SpringJobScheduler;
import com.dangdang.ddframe.job.reg.zookeeper.ZookeeperRegistryCenter;
import com.google.common.base.Optional;
import com.xpedia.elastic.job.annotation.EsJobConf;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * EsJob解释器
 *
 * @author Xpedia
 */
@Slf4j
public class JobConfParser implements ApplicationContextAware {
    /**
     * ZK注册中心
     */
    private final ZookeeperRegistryCenter zookeeperRegistryCenter;
    /**
     * elastic-job管理地址
     */
    private final JobOperateAPI jobOperateAPI;
    /**
     * 所有初始化后的JobNameMap集合(所有携带EsJobConf注解的类都在)
     */
    private final Map<String, ElasticJob> initializeJobMap = new HashMap<>();
    /**
     * 当前服务器执行任务集合（当前任务执行列表）
     */
    private Set<String> currentServerExecuteTaskSet;
    /**
     * 是否执行全部任务
     */
    private boolean executeAll;

    public JobConfParser(ZookeeperRegistryCenter zookeeperRegistryCenter,
                         Set<String> currentServerExecuteTaskSet,
                         JobOperateAPI joboperateapi,
                         boolean executeAll) {
        this.zookeeperRegistryCenter = zookeeperRegistryCenter;
        this.currentServerExecuteTaskSet = currentServerExecuteTaskSet;
        this.jobOperateAPI = joboperateapi;
        this.executeAll = executeAll;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        //初始化所有Job集合
        initJobMap(applicationContext);
        //过滤当前机房执行任务
        initCurrentServerJobs();
        log.info("elastic job current node execute job set is : {}", currentServerExecuteTaskSet);
        //初始化执行任务
        currentServerExecuteTaskSet.forEach(this::initScheduler);
    }

    private void initCurrentServerJobs() {
        //全部执行
        if (executeAll) {
            currentServerExecuteTaskSet = initializeJobMap.keySet();
            return;
        }
        //执行合法部分
        currentServerExecuteTaskSet.removeIf(x -> !initializeJobMap.containsKey(x));
    }

    /**
     * 特暴露改方法用于动态替换执行任务
     *
     * @param executeAll   是否执行全部任务
     * @param targetJobSet 变更后的执行任务集合
     */
    @SuppressWarnings("all")
    public void modifyCurrentServerExecuteJobSet(boolean executeAll, Set<String> targetJobSet) {
        this.executeAll = executeAll;
        //校验变更后的任务集合合法性
        for (String jobName : targetJobSet) {
            if (!initializeJobMap.containsKey(jobName)) {
                return;
            }
        }
        //增加任务
        targetJobSet.forEach(jobName -> {
            if (!initializeJobMap.containsKey(jobName)) {
                initScheduler(jobName);
            }
            jobOperateAPI.enable(Optional.fromNullable(jobName), Optional.fromNullable(null));
        });
        //disable相关任务
        currentServerExecuteTaskSet.forEach(jobName -> {
            if (!targetJobSet.contains(jobName)) {
                jobOperateAPI.disable(Optional.fromNullable(jobName), Optional.fromNullable(null));
            }
        });
        //当前执行任务集合更新
        this.currentServerExecuteTaskSet = targetJobSet;

    }

    /**
     * 初始化应用所有任务列表
     *
     * @param ctx applicationContext
     */
    private void initJobMap(ApplicationContext ctx) {
        log.info("elastic job initJobMap begins ! ");
        Map<String, ElasticJob> jobMap = ctx.getBeansOfType(ElasticJob.class);
        jobMap.forEach((key, value) -> {
            //获取ElasticJob上的配置
            EsJobConf jobConf = AnnotationUtils.findAnnotation(value.getClass(), EsJobConf.class);
            if (jobConf == null) {
                return;
            }
            initializeJobMap.putIfAbsent(jobConf.name(), value);
        });
        log.info("elastic job initJobMap finished ! initializeJobMap :{}", initializeJobMap.keySet());
    }

    /**
     * 初始化SpringScheduler 进行任务执行
     *
     * @param jobName 任务名称
     */
    private void initScheduler(String jobName) {
        ElasticJob elasticJob = initializeJobMap.get(jobName);
        //获取ElasticJob上的配置
        EsJobConf jobConf = AnnotationUtils.findAnnotation(elasticJob.getClass(), EsJobConf.class);
        if (jobConf == null) {
            return;
        }
        //初始化ElasticJob
        JobCoreConfiguration jobCoreConfiguration = this.initJobCoreConf(jobConf);
        JobTypeConfiguration jobTypeConfiguration;
        if (elasticJob instanceof SimpleJob) {
            //init simple job
            jobTypeConfiguration = new SimpleJobConfiguration(jobCoreConfiguration, elasticJob.getClass().getName());
        } else if (elasticJob instanceof DataflowJob) {
            jobTypeConfiguration = new DataflowJobConfiguration(jobCoreConfiguration, elasticJob.getClass().getName(), jobConf.streamingProcess());
        } else if (elasticJob instanceof ScriptJob) {
            jobTypeConfiguration = new ScriptJobConfiguration(jobCoreConfiguration, elasticJob.getClass().getName());
        } else {
            return;
        }
        LiteJobConfiguration liteJobConfiguration = this.initLiteJobConf(jobConf, jobTypeConfiguration);
        //初始化JobScheduler
        SpringJobScheduler scheduler = new SpringJobScheduler(elasticJob, this.zookeeperRegistryCenter, liteJobConfiguration);
        scheduler.init();
        log.info("elastic job {} job scheduler init succ! ,cron:{}, sharding:{}", jobConf.name(), jobConf.cron(), jobConf.shardingTotalCount());
    }

    private JobCoreConfiguration initJobCoreConf(EsJobConf jobConf) {
        return JobCoreConfiguration.newBuilder(jobConf.name(), jobConf.cron(), jobConf.shardingTotalCount())
                .description(jobConf.description())
                .failover(jobConf.failover())
                .misfire(jobConf.misfire())
                .jobParameter(jobConf.jobParameter())
                .shardingItemParameters(jobConf.shardingItemParameters())
                .build();
    }

    private LiteJobConfiguration initLiteJobConf(EsJobConf jobConf, JobTypeConfiguration jobTypeConfiguration) {
        return LiteJobConfiguration.newBuilder(jobTypeConfiguration)
                .disabled(jobConf.disabled())
                .overwrite(jobConf.overwrite())
                .jobShardingStrategyClass(AverageAllocationJobShardingStrategy.class.getName())
                .build();
    }
}
