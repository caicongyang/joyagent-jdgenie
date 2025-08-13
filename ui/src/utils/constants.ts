/**
 * 预览状态的tab选项
*/
import csvIcon from '@/assets/icon/CSV.png';
import docxIcon from '@/assets/icon/docx.png';
import excleIcon from '@/assets/icon/excle.png';
import pdfIcon from '@/assets/icon/pdf.png';
import txtIcon from '@/assets/icon/txt.png';
import htmlIcon  from '@/assets/icon/HTML.png';
import demo1 from '@/assets/icon/demo1.png';
import demo2 from '@/assets/icon/demo2.png';
import demo3 from '@/assets/icon/demo3.png';
import demo4 from '@/assets/icon/demo4.png';

import { ActionViewItemEnum } from "./enums";

export const iconType:Record<string, string> = {
  doc: docxIcon,
  docx: docxIcon,
  xlsx: excleIcon,
  csv: csvIcon,
  pdf: pdfIcon,
  txt: txtIcon,
  html: htmlIcon,
};

export const actionViewOptions = [
  {
    label: '实时跟随',
    value: ActionViewItemEnum.follow,
    split: false
  },
  {
    label: '浏览器',
    value: ActionViewItemEnum.browser,
  },
  {
    label: '文件',
    value: ActionViewItemEnum.file
  }
];

export const defaultActiveActionView = actionViewOptions[0].value;

export const productList = [{
  name: '网页模式',
  img: 'icon-diannao',
  type: 'html',
  placeholder: 'Genie会完成你的任务并以HTML网页方式输出报告',
  color: 'text-[#29CC29]'
},
{
  name: '文档模式',
  img: 'icon-wendang',
  type: 'docs',
  placeholder: 'Genie会完成你的任务并以markdown格式输出文档',
  color: 'text-[#4040FF]'
},
{
  name: 'PPT模式',
  img: 'icon-ppt',
  type: 'ppt',
  placeholder: 'Genie会完成你的任务并以PPT方式输出结论',
  color: 'text-[#FF860D]'
},
{
  name: '表格模式',
  img: 'icon-biaoge',
  type: 'table',
  placeholder: 'Genie会完成你的任务并以表格格式输出结论',
  color: 'text-[#FF3333]'
}];

export const defaultProduct = productList[0];

export const RESULT_TYPES = ['task_summary', 'result'];

export const InputSize:Record<string, string>  = {
  big: '106',
  medium: '72',
  small: '32'
};

// 智能体类型枚举
export enum AgentTypeEnum {
  COMPREHENSIVE = 1,
  WORKFLOW = 2,
  PLAN_SOLVE = 3,
  ROUTER = 4,
  REACT = 5,
  PROMPT_FLOW = 6
}

// 智能体模式配置
export const agentModeList = [
  {
    name: '快速响应',
    key: 'react',
    agentType: AgentTypeEnum.REACT,
    description: '快速响应模式，适合简单问答和基础任务',
    icon: 'icon-kuaisuchuli',
    color: 'text-[#29CC29]'
  },
  {
    name: '深度研究',
    key: 'plan_solve', 
    agentType: AgentTypeEnum.PLAN_SOLVE,
    description: '深度研究模式，适合复杂分析和研究任务',
    icon: 'icon-shendusikao',
    color: 'text-[#4040FF]'
  },
  {
    name: '工作流',
    key: 'workflow',
    agentType: AgentTypeEnum.WORKFLOW,
    description: '工作流模式，适合多步骤复杂业务流程处理',
    icon: 'icon-gongzuoliu',
    color: 'text-[#FF860D]'
  },
  {
    name: 'Prompt流程',
    key: 'prompt_flow',
    agentType: AgentTypeEnum.PROMPT_FLOW,
    description: '基于Markdown的流程化智能体，支持自定义流程步骤',
    icon: 'icon-liucheng',
    color: 'text-[#9C27B0]'
  }
];

export const defaultAgentMode = agentModeList[0];

// 工作流模板配置
export const workflowTemplates = [
  {
    id: 'data_analysis',
    name: '数据分析工作流',
    description: '包含数据收集、处理、分析和报告生成的完整流程',
    steps: ['数据收集', '数据处理', '数据分析', '结果报告'],
    icon: 'icon-shujufenxi',
    color: 'text-[#4040FF]'
  },
  {
    id: 'research', 
    name: '研究工作流',
    description: '包含研究计划、信息搜索、分析和报告的研究流程',
    steps: ['制定研究计划', '信息搜索', '信息分析', '研究报告'],
    icon: 'icon-yanjiu',
    color: 'text-[#29CC29]'
  },
  {
    id: 'problem_solving',
    name: '问题解决工作流', 
    description: '包含问题分析、方案设计、评估和实施建议的问题解决流程',
    steps: ['问题分析', '方案设计', '方案评估', '实施建议'],
    icon: 'icon-jiejuefangan',
    color: 'text-[#FF860D]'
  }
];

export const demoList = [
  {
    title: 'Browser代码架构分析',
    description: '帮我分析github中开源的browser-use的代码，并进行分析',
    tag: '专业研究',
    videoUrl: 'https://private-user-images.githubusercontent.com/49786633/469170308-065b8d1a-92e4-470a-bbe3-426fafeca5c4.mp4?jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NTM2OTE1NDIsIm5iZiI6MTc1MzY5MTI0MiwicGF0aCI6Ii80OTc4NjYzMy80NjkxNzAzMDgtMDY1YjhkMWEtOTJlNC00NzBhLWJiZTMtNDI2ZmFmZWNhNWM0Lm1wND9YLUFtei1BbGdvcml0aG09QVdTNC1ITUFDLVNIQTI1NiZYLUFtei1DcmVkZW50aWFsPUFLSUFWQ09EWUxTQTUzUFFLNFpBJTJGMjAyNTA3MjglMkZ1cy1lYXN0LTElMkZzMyUyRmF3czRfcmVxdWVzdCZYLUFtei1EYXRlPTIwMjUwNzI4VDA4MjcyMlomWC1BbXotRXhwaXJlcz0zMDAmWC1BbXotU2lnbmF0dXJlPWNlOWNiZmZkMzdjNDUxODc4YjMyNDE1ZmU4ZjlmZjgwZjYxMzRlNWMwNmFlZjM1M2Q3ZDNlNDYzOTUzNmZlMTAmWC1BbXotU2lnbmVkSGVhZGVycz1ob3N0In0.6OwtEGxcMnWlSCMgl0AaNy8NRl9lLuLx-nXrXdHLETg',
    url: '//storage.360buyimg.com/pubfree-bucket/ei-data-resource/89ab083/static/demoPage.html',
    image: demo1
  },
  {
    title: '京东财报分析',
    description: '分析一下京东的最新财务报告，总结出核心数据以及公司发展情况',
    tag: '数据分析',
    videoUrl: 'https://private-user-images.githubusercontent.com/49786633/469171050-15dcf089-5659-489e-849d-39c651ca7e5a.mp4?jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NTM2OTE5ODgsIm5iZiI6MTc1MzY5MTY4OCwicGF0aCI6Ii80OTc4NjYzMy80NjkxNzEwNTAtMTVkY2YwODktNTY1OS00ODllLTg0OWQtMzljNjUxY2E3ZTVhLm1wND9YLUFtei1BbGdvcml0aG09QVdTNC1ITUFDLVNIQTI1NiZYLUFtei1DcmVkZW50aWFsPUFLSUFWQ09EWUxTQTUzUFFLNFpBJTJGMjAyNTA3MjglMkZ1cy1lYXN0LTElMkZzMyUyRmF3czRfcmVxdWVzdCZYLUFtei1EYXRlPTIwMjUwNzI4VDA4MzQ0OFomWC1BbXotRXhwaXJlcz0zMDAmWC1BbXotU2lnbmF0dXJlPTY0MDE1OWQ1NjNiNTcwZGY1ZTBhNzllNDhhMjM3M2E3YjQ3Mzc4ZjYwN2ExMWUxMTZjYzIwZWIzOGFhYjEzYjkmWC1BbXotU2lnbmVkSGVhZGVycz1ob3N0In0.QqNCtSyGy20QbeNPPib6zVLpzPrcKmDMHJFphAwzx6E',
    url: '//storage.360buyimg.com/pubfree-bucket/ei-data-resource/89ab083/static/demoPage2.html',
    image: demo2
  },
  {
    title: 'HR智能招聘产品竞品分析',
    description: '分析一下HR智能招聘领域的优秀产品，形成一个竞品对比报告',
    tag: '竞品调研',
    videoUrl: 'https://private-user-images.githubusercontent.com/49786633/469171112-cd99e2f8-9887-459f-ae51-00e7883fa050.mp4?jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NTM2OTE5ODgsIm5iZiI6MTc1MzY5MTY4OCwicGF0aCI6Ii80OTc4NjYzMy80NjkxNzExMTItY2Q5OWUyZjgtOTg4Ny00NTlmLWFlNTEtMDBlNzg4M2ZhMDUwLm1wND9YLUFtei1BbGdvcml0aG09QVdTNC1ITUFDLVNIQTI1NiZYLUFtei1DcmVkZW50aWFsPUFLSUFWQ09EWUxTQTUzUFFLNFpBJTJGMjAyNTA3MjglMkZ1cy1lYXN0LTElMkZzMyUyRmF3czRfcmVxdWVzdCZYLUFtei1EYXRlPTIwMjUwNzI4VDA4MzQ0OFomWC1BbXotRXhwaXJlcz0zMDAmWC1BbXotU2lnbmF0dXJlPTA2MDNiNDk5MThlZTRhMTY0YTM0YWQ1MGU2NDRlYzg1NWIxNDM4ZmYyMmE1MTY2YzgwZmUyOTI1MjY3NjFiNTQmWC1BbXotU2lnbmVkSGVhZGVycz1ob3N0In0.-r9MhEJ9RgbYPi-cTCmG0wMxNmFC0rjXNMti4LRvspc',
    url: '//storage.360buyimg.com/pubfree-bucket/ei-data-resource/89ab083/static/demoPage3.html',
    image: demo3
  },
  {
    title: '超市销售数据分析',
    description: '帮我分析一下国内销售数据',
    tag: '数据分析',
    videoUrl: 'https://private-user-images.githubusercontent.com/49786633/469171151-657bbe61-5516-4ab9-84c2-c6ca75cc4a6f.mp4?jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJnaXRodWIuY29tIiwiYXVkIjoicmF3LmdpdGh1YnVzZXJjb250ZW50LmNvbSIsImtleSI6ImtleTUiLCJleHAiOjE3NTM2OTE5ODgsIm5iZiI6MTc1MzY5MTY4OCwicGF0aCI6Ii80OTc4NjYzMy80NjkxNzExNTEtNjU3YmJlNjEtNTUxNi00YWI5LTg0YzItYzZjYTc1Y2M0YTZmLm1wND9YLUFtei1BbGdvcml0aG09QVdTNC1ITUFDLVNIQTI1NiZYLUFtei1DcmVkZW50aWFsPUFLSUFWQ09EWUxTQTUzUFFLNFpBJTJGMjAyNTA3MjglMkZ1cy1lYXN0LTElMkZzMyUyRmF3czRfcmVxdWVzdCZYLUFtei1EYXRlPTIwMjUwNzI4VDA4MzQ0OFomWC1BbXotRXhwaXJlcz0zMDAmWC1BbXotU2lnbmF0dXJlPTJkMDNlNTkxNzFkNjFlYTI1MTAzNTIyZWM0YzA1MzE5MTY4NDYyYTg5MjUxZWY0Mjg0OWU1ODUxNGZkNTU3ZTEmWC1BbXotU2lnbmVkSGVhZGVycz1ob3N0In0.BRatyWFZm91TAvRn1iss7DMPWLXIoRm9geqaN6af7cI',
    url: '//storage.360buyimg.com/pubfree-bucket/ei-data-resource/89ab083/static/demoPage4.html',
    image: demo4
  }
];