# 智能客服对话流程示例

## 配置
- 公司: JoyAgent
- 服务等级: 高级
- 模型: gpt-3.5-turbo

## 流程步骤

1. **问候客户** [prompt]
   > 你是 {{公司}} 的智能客服助手，请礼貌专业地问候客户。
   > 
   > 服务等级: {{服务等级}}
   > 当前时间: {{current_time}}
   > 
   > 客户消息: {{user_input}}

2. **意图识别** [tool:deep_search]
   - query: 客服意图识别：{{user_input}}

3. **咨询处理** [if:{{step_2_result}} contains "咨询"]
   
   3.1. **知识库搜索** [tool:deep_search]
        - query: 产品咨询 {{user_input}}
   
   3.2. **生成回复** [prompt]
        > 基于知识库搜索结果，为客户提供准确的回复：
        > 
        > 客户问题: {{user_input}}
        > 知识库结果: {{step_3_1_result}}
        > 
        > 要求：专业、友好、准确

4. **投诉处理** [if:{{step_2_result}} contains "投诉"]
   
   4.1. **记录投诉** [tool:file_tool]
        - 操作: upload
        - 文件名: 客户投诉记录_{{current_date}}.md
        - 描述: 客户投诉记录
        - 内容: |
          # 客户投诉记录
          - 时间: {{current_time}}
          - 投诉内容: {{user_input}}
          - 状态: 已记录
   
   4.2. **安抚回复** [prompt]
        > 客户提出了投诉，请给出专业的安抚回复：
        > 
        > 投诉内容: {{user_input}}
        > 
        > 要求：
        > - 表示歉意和重视
        > - 说明处理流程
        > - 给出预期时间

5. **回复总结** [prompt]
   > 请对本次客服对话进行总结：
   > 
   > 客户意图: {{step_2_result}}
   > 处理结果: {{step_3_result}}{{step_4_result}}
   > 
   > 生成简洁的服务总结。