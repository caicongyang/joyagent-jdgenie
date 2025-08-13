# 销售数据分析流程示例

## 配置
- 作者: AI数据分析师
- 模型: gpt-4
- 温度: 0.1
- 最大令牌: 4000

## 流程步骤

1. **数据文件读取** [tool:file_tool]
   - 操作: get
   - 文件名: {{user_file}}

2. **初步数据探索** [tool:code_interpreter]
   ```python
   import pandas as pd
   import matplotlib.pyplot as plt
   
   # 读取数据
   df = pd.read_csv('{{user_file}}')
   print("数据形状:", df.shape)
   print("数据类型:")
   print(df.dtypes)
   print("前5行数据:")
   print(df.head())
   ```

3. **数据质量检查** [tool:code_interpreter]
   ```python
   # 检查缺失值
   missing_data = df.isnull().sum()
   print("缺失值统计:")
   print(missing_data[missing_data > 0])
   
   # 基础统计信息
   print("数值列统计:")
   print(df.describe())
   ```

4. **生成分析报告** [prompt]
   > 你是一名资深数据分析师，请基于以下数据分析结果生成一份专业的销售分析报告：
   > 
   > 数据概览: {{step_2_result}}
   > 数据质量: {{step_3_result}}
   > 
   > 请按以下结构输出报告：
   > 1. 执行摘要
   > 2. 数据概览
   > 3. 关键发现
   > 4. 数据质量评估
   > 5. 行动建议

5. **格式化输出** [markdown]
   ```markdown
   # 销售数据分析报告
   
   **分析师**: {{作者}}  
   **分析时间**: {{current_date}}  
   **数据文件**: {{user_file}}
   
   {{step_4_result}}
   
   ---
   *本报告由 PromptFlow Agent 自动生成*
   ```