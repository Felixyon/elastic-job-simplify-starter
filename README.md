# elastic-job-simplify-starter
elastis-job 配置注解化，3分钟任务接入elastic-job

***

## QuickStart

```java
1.任务调度依赖ZOOKEEPER，下面使用docker 快速部署一个zookeeper节点

#下载zookeeper docker镜像
docker pull zookeeper:latest

#启动ZK信息
docker run -p 20181:2181 -P --name regular_zookeeper2 --restart always -d zookeeper

2.按照下面基本配置【依赖配置】和业务逻辑中的【关键注解】
properties中配置：

elastic.job.zk.serverLists=localhost:20181  #ZK NODE地址
elastic.job.zk.namespace=xpedia			#自定义namaspace空间，用于去elastic-job控制台添加
elastic.job.execute.all=true			#是否忽略自定义job配置，执行所有携带注解的job

3.配置完成，启动即可使用
```



***



## 基本配置

***



### 1.依赖配置

```maven
<!--maven properties-->
		<elastic-job.version>2.1.5</elastic-job.version>
		<guava.version>29.0-jre</guava.version>
		<curator.version>2.12.0</curator.version>
<!--dependencies -->
		<!--ZK配置，此版本建议不要调整-->
		<dependency>
			<groupId>org.apache.curator</groupId>
			<artifactId>curator-framework</artifactId>
			<version>${curator.version}</version>
		</dependency>
		<dependency>
			<groupId>org.apache.curator</groupId>
			<artifactId>curator-client</artifactId>
			<version>${curator.version}</version>
		</dependency>
		<dependency>
			<groupId>org.apache.curator</groupId>
			<artifactId>curator-recipes</artifactId>
			<version>${curator.version}</version>
		</dependency>
		<!--guava配置，与上方ZK配置兼容，建议不要调整-->
		<dependency>
			<groupId>com.google.guava</groupId>
			<artifactId>guava</artifactId>
			<version>${guava.version}</version>
		</dependency>
		<!--引入elastic-job核心配置-->
		<dependency>
			<groupId>com.dangdang</groupId>
			<artifactId>elastic-job-lite-core</artifactId>
			<version>${elastic-job.version}</version>
			<exclusions>
				<exclusion>
					<artifactId>guava</artifactId>
					<groupId>com.google.guava</groupId>
				</exclusion>
			</exclusions>
		</dependency>
		<dependency>
			<groupId>com.dangdang</groupId>
			<artifactId>elastic-job-lite-spring</artifactId>
			<version>${elastic-job.version}</version>
			<exclusions>
				<exclusion>
					<artifactId>guava</artifactId>
					<groupId>com.google.guava</groupId>
				</exclusion>
			</exclusions>
		</dependency>
		<dependency>
			<groupId>com.dangdang</groupId>
			<artifactId>elastic-job-lite-lifecycle</artifactId>
			<version>${elastic-job.version}</version>
			<exclusions>
				<exclusion>
					<artifactId>guava</artifactId>
					<groupId>com.google.guava</groupId>
				</exclusion>
			</exclusions>
		</dependency>
		

```

***

### 2.properties配置

```properties
elastic.job.zk.serverLists=localhost:55003  #ZK NODE地址
elastic.job.zk.namespace=xpedia			#自定义namaspace空间，用于去elastic-job控制台添加
elastic.job.execute.list=testJob		#自定义执行job名称，英文逗号分割
elastic.job.execute.all=false			#是否忽略自定义job配置，执行所有携带注解的job
```

***

### 3.业务逻辑使用

#### 3.1 关键注解

```java
1.@EnableElasticJob  该注解添加到SpringBootApplication的启动位置

@EnableElasticJob
@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
		CountDownLatch countDownLatch = new CountDownLatch(1);
		try {
			countDownLatch.await();
		} catch (InterruptedException e) {

		}
	}

}

2.@EsJobConf(name="",cron="") 该注解添加到实现了SimpleJob（要执行的任务类上）

/**
 * 测试JOB类
 * @author Xpedia
 */
@EsJobConf(name = "testJob", cron = "0/10 * * * * ? *")
public class TestJob implements SimpleJob {
    @Override
    public void execute(ShardingContext shardingContext) {
        System.out.println("helloworld!");
    }
}

```

#### 3.2 自定义业务拓展支持

```java
1.暴露Bean   JobConfParser jobConfParser
	该bean暴露方法：
	modifyCurrentServerExecuteJobSet(boolean executeAll, Set<String> targetJobSet)
	根据业务要求，动态替换当前server node 执行任务列表
	executeAll=true 不考虑配置，执行全部标注@EsJobConf的Job集合
	targetJobSet={}  该配置旨在executeAll=false生效，当前server node执行任务列表
	（备注：动态修改任务，删除掉的任务调用方式为disable，后续还可以将该任务添加回来）
	
2.暴露Bean JobOperateAPI jobOperateApi
	该bean可调度elastic-job管理页面的
	trigger
	disable
	enable
	shutdown
	remove
	等操作
	可根据业务自定义开发使用

```

***

其他：

本项目参考：https://github.com/yinjihuan/elastic-job-spring-boot-starter

去除了所有的REST暴露的API接口，适合spring-boot简化快速搭建