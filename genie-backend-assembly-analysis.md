# Genie Backend Assembly 工作原理分析

## 1. Assembly 简介

### 什么是Maven Assembly Plugin？

Maven Assembly Plugin 是一个Maven插件，用于创建包含项目输出和依赖项的分发包。它可以将Java应用程序及其所有依赖项打包成一个可独立运行的分发包，便于部署和分发。

### Assembly的主要功能

- **依赖聚合**：将所有依赖的JAR包收集到一个目录中
- **资源整合**：包含配置文件、脚本文件等资源
- **结构定制**：按照指定的目录结构组织文件
- **格式支持**：支持多种打包格式（ZIP、TAR、DIR等）

## 2. Genie Backend 中的Assembly 配置

### 2.1 POM.xml 中的Assembly插件配置

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-assembly-plugin</artifactId>
    <configuration>
        <finalName>${project.name}</finalName>
        <appendAssemblyId>false</appendAssemblyId>
        <descriptors>
            <descriptor>src/main/assembly/assembly.xml</descriptor>
        </descriptors>
    </configuration>
    <executions>
        <execution>
            <id>make-assembly</id>
            <phase>package</phase>
            <goals>
                <goal>single</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**配置参数说明：**
- `finalName`：生成包的名称（不含扩展名）
- `appendAssemblyId`：是否在文件名后追加assembly ID
- `descriptors`：指定assembly描述符文件的位置
- `phase`：在Maven的package阶段执行
- `goal`：执行single目标进行打包

### 2.2 Assembly 描述符文件 (assembly.xml)

```xml
<assembly>
    <id>package</id>
    <formats>
        <format>dir</format>
        <format>zip</format>
    </formats>
    <includeBaseDirectory>false</includeBaseDirectory>
    
    <!-- 文件集合配置 -->
    <fileSets>
        <!-- 启动停止脚本 -->
        <fileSet>
            <directory>src/main/assembly/bin</directory>
            <outputDirectory>bin</outputDirectory>
            <includes>
                <include>start.sh</include>
                <include>stop.sh</include>
            </includes>
        </fileSet>
        
        <!-- 配置文件 -->
        <fileSet>
            <directory>src/main/resources</directory>
            <outputDirectory>conf</outputDirectory>
            <filtered>true</filtered>
        </fileSet>
    </fileSets>

    <!-- 依赖包配置 -->
    <dependencySets>
        <dependencySet>
            <outputDirectory>lib</outputDirectory>
        </dependencySet>
    </dependencySets>
</assembly>
```

**配置说明：**
- `formats`：生成dir（目录）和zip（压缩包）两种格式
- `includeBaseDirectory`：不包含基础目录层级
- `fileSets`：定义要包含的文件和目录
- `dependencySets`：处理Maven依赖项

## 3. Assembly 工作流程

### 3.1 构建流程

1. **编译阶段**：Maven编译源代码
2. **打包阶段**：创建项目的JAR文件
3. **Assembly阶段**：根据assembly.xml配置创建分发包

### 3.2 执行命令

```bash
# 完整构建命令
mvn clean package -DskipTests -s aliyun-settings.xml
```

**构建过程：**
1. `clean`：清理之前的构建结果
2. `package`：编译并打包项目，同时触发assembly插件
3. `-DskipTests`：跳过测试
4. `-s aliyun-settings.xml`：使用阿里云Maven镜像

### 3.3 生成的目录结构

```
target/
└── genie-backend/
    ├── bin/
    │   ├── start.sh    # 启动脚本
    │   └── stop.sh     # 停止脚本
    ├── conf/
    │   └── application.yml  # 配置文件
    └── lib/
        ├── genie-backend-0.0.1-SNAPSHOT.jar  # 主应用JAR
        ├── spring-boot-starter-web-xxx.jar   # Spring Boot依赖
        ├── jackson-databind-xxx.jar          # JSON处理依赖
        └── ... (其他依赖JAR包)
```

## 4. 启动脚本分析 (start.sh)

### 4.1 脚本功能

```bash
#!/bin/sh
set -x

# 环境配置
if [ -f /home/admin/default_vm.sh ]; then
  source /home/admin/default_vm.sh
fi

# 路径配置
SHDIR=$(cd "$(dirname "$0")"; pwd)
BASEDIR=$(cd $SHDIR/..; pwd)
APP_NAME="genie-backend"

# 日志配置
LOGDIR=/export/Logs/$APP_NAME
LOGFILE="$LOGDIR/${APP_NAME}_startup.log"

# 类路径配置
CLASSPATH="$BASEDIR/conf/:$BASEDIR/lib/*"
MAIN_MODULE="com.jd.genie.GenieApplication"

# 主JAR文件
PROJECT_NAME="genie-backend"
JAR_NAME="${PROJECT_NAME}-0.0.1-SNAPSHOT.jar"
MAIN_JAR="${BASEDIR}/lib/${JAR_NAME}"
```

### 4.2 启动逻辑

1. **环境检查**：检查应用是否已经运行
2. **目录创建**：创建日志目录
3. **进程启动**：使用java命令启动应用
4. **状态验证**：检查应用是否启动成功

### 4.3 关键特性

- **进程检测**：通过classpath路径识别进程，避免误杀
- **后台运行**：使用setsid实现真正的后台运行
- **日志重定向**：将输出重定向到日志文件
- **启动验证**：启动后检查进程是否存在

## 5. 停止脚本分析 (stop.sh)

### 5.1 停止逻辑

```bash
#!/bin/sh

# 路径配置
SHDIR=$(cd "$(dirname "$0")"; pwd)
BASEDIR=$(cd $SHDIR/..; pwd)
APP_NAME="genie-backend"
CLASSPATH="$BASEDIR/conf/:$BASEDIR/lib/*"

# 进程查找函数
function get_pid() {
    pgrep -lf "java .* $CLASSPATH"
}

# 停止逻辑
if ! get_pid; then
    echo "App not running, exit"
else
    pkill -9 -f "java .* $CLASSPATH"
    sleep 5
fi
```

### 5.2 安全特性

- **精确匹配**：通过完整的classpath路径匹配进程
- **强制终止**：使用kill -9强制终止进程
- **状态检查**：停止后验证进程是否真正终止

## 6. Assembly 的优点

### 6.1 部署优势

1. **自包含**：包含所有运行时依赖
2. **标准化**：统一的目录结构和启动方式
3. **便携性**：可以在任何支持Java的环境中运行
4. **版本控制**：所有依赖版本固定，避免版本冲突

### 6.2 运维优势

1. **简单部署**：解压即可运行
2. **脚本化**：提供标准的启停脚本
3. **日志管理**：统一的日志输出位置
4. **进程管理**：安全的进程启停机制

## 7. 使用方法

### 7.1 构建应用

```bash
cd genie-backend
./build.sh
```

### 7.2 启动应用

```bash
# 方式1：使用项目根目录的启动脚本
./start.sh

# 方式2：使用assembly生成的启动脚本
cd target/genie-backend
./bin/start.sh
```

### 7.3 停止应用

```bash
cd target/genie-backend
./bin/stop.sh
```

## 8. 总结

Genie Backend项目的Assembly配置实现了：

1. **完整的打包方案**：将应用程序、依赖库、配置文件和运行脚本整合在一起
2. **标准化的部署结构**：遵循常见的Java应用部署规范
3. **便捷的运维操作**：提供简单易用的启停脚本
4. **生产环境适配**：考虑了日志管理、进程监控等生产环境需求

这种Assembly配置方式特别适合传统的Java企业级应用部署，提供了良好的部署体验和运维便利性。 