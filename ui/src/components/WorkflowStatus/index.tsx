import React from 'react';
import { Progress } from 'antd';
import classNames from 'classnames';

interface WorkflowStatusProps {
  messageType: string;
  resultMap?: any;
  className?: string;
}

const WorkflowStatus: React.FC<WorkflowStatusProps> = ({ 
  messageType, 
  resultMap = {}, 
  className 
}) => {
  const getStatusIcon = () => {
    switch (messageType) {
      case 'workflow_start':
        return <i className="font_family icon-gongzuoliu text-blue-500 text-lg"></i>;
      case 'workflow_progress':
        return <i className="font_family icon-jindu text-orange-500 text-lg"></i>;
      case 'workflow_step':
        return <i className="font_family icon-buzhou text-green-500 text-lg"></i>;
      case 'workflow_complete':
        return <i className="font_family icon-wancheng text-green-600 text-lg"></i>;
      case 'workflow_error':
        return <i className="font_family icon-cuowu text-red-500 text-lg"></i>;
      default:
        return <i className="font_family icon-gongzuoliu text-gray-500 text-lg"></i>;
    }
  };

  const getStatusText = () => {
    switch (messageType) {
      case 'workflow_start':
        return '工作流启动中...';
      case 'workflow_progress':
        return '工作流执行中...';
      case 'workflow_step':
        return '步骤执行中...';
      case 'workflow_complete':
        return '工作流执行完成';
      case 'workflow_error':
        return '工作流执行出错';
      default:
        return '工作流处理中...';
    }
  };

  const getStatusColor = () => {
    switch (messageType) {
      case 'workflow_start':
        return 'text-blue-600';
      case 'workflow_progress':
        return 'text-orange-600';
      case 'workflow_step':
        return 'text-green-600';
      case 'workflow_complete':
        return 'text-green-700';
      case 'workflow_error':
        return 'text-red-600';
      default:
        return 'text-gray-600';
    }
  };

  const renderContent = () => {
    switch (messageType) {
      case 'workflow_start':
        return (
          <div className="space-y-3">
            <div className="flex items-center gap-3">
              {getStatusIcon()}
              <span className={classNames('font-medium', getStatusColor())}>
                {getStatusText()}
              </span>
            </div>
            {resultMap.name && (
              <div className="bg-blue-50 p-3 rounded-lg">
                <div className="text-sm text-gray-700">
                  <span className="font-medium">工作流名称：</span>
                  {resultMap.name}
                </div>
                {resultMap.description && (
                  <div className="text-sm text-gray-600 mt-1">
                    <span className="font-medium">描述：</span>
                    {resultMap.description}
                  </div>
                )}
                {resultMap.totalSteps && (
                  <div className="text-sm text-gray-600 mt-1">
                    <span className="font-medium">总步骤数：</span>
                    {resultMap.totalSteps}
                  </div>
                )}
              </div>
            )}
          </div>
        );

      case 'workflow_progress':
        const progress = resultMap.progress ? Math.round(resultMap.progress * 100) : 0;
        return (
          <div className="space-y-3">
            <div className="flex items-center gap-3">
              {getStatusIcon()}
              <span className={classNames('font-medium', getStatusColor())}>
                {getStatusText()}
              </span>
            </div>
            <div className="bg-orange-50 p-3 rounded-lg">
              <div className="flex items-center justify-between mb-2">
                <span className="text-sm font-medium text-gray-700">执行进度</span>
                <span className="text-sm text-gray-600">{progress}%</span>
              </div>
              <Progress 
                percent={progress} 
                strokeColor="#f97316"
                size="small"
                showInfo={false}
              />
              {resultMap.currentStep && (
                <div className="text-sm text-gray-600 mt-2">
                  <span className="font-medium">当前步骤：</span>
                  {resultMap.currentStep}
                </div>
              )}
              {resultMap.completedSteps !== undefined && resultMap.totalSteps && (
                <div className="text-sm text-gray-600 mt-1">
                  <span className="font-medium">已完成：</span>
                  {resultMap.completedSteps}/{resultMap.totalSteps} 步骤
                </div>
              )}
            </div>
          </div>
        );

      case 'workflow_step':
        return (
          <div className="space-y-3">
            <div className="flex items-center gap-3">
              {getStatusIcon()}
              <span className={classNames('font-medium', getStatusColor())}>
                步骤完成
              </span>
            </div>
            <div className="bg-green-50 p-3 rounded-lg">
              {resultMap.stepName && (
                <div className="text-sm text-gray-700 mb-2">
                  <span className="font-medium">步骤名称：</span>
                  {resultMap.stepName}
                </div>
              )}
              {resultMap.stepResult && (
                <div className="text-sm text-gray-600">
                  <span className="font-medium">执行结果：</span>
                  <div className="mt-1 p-2 bg-white rounded border text-gray-700">
                    {resultMap.stepResult}
                  </div>
                </div>
              )}
            </div>
          </div>
        );

      case 'workflow_complete':
        return (
          <div className="space-y-3">
            <div className="flex items-center gap-3">
              {getStatusIcon()}
              <span className={classNames('font-medium', getStatusColor())}>
                {getStatusText()}
              </span>
            </div>
            <div className="bg-green-50 p-3 rounded-lg">
              {resultMap.executionTime && (
                <div className="text-sm text-gray-700 mb-2">
                  <span className="font-medium">执行时间：</span>
                  {Math.round(resultMap.executionTime / 1000)}秒
                </div>
              )}
              {resultMap.totalSteps && (
                <div className="text-sm text-gray-700 mb-2">
                  <span className="font-medium">完成步骤：</span>
                  {resultMap.totalSteps}
                </div>
              )}
              {resultMap.result && (
                <div className="text-sm text-gray-600">
                  <span className="font-medium">最终结果：</span>
                  <div className="mt-1 p-2 bg-white rounded border text-gray-700">
                    {resultMap.result}
                  </div>
                </div>
              )}
            </div>
          </div>
        );

      case 'workflow_error':
        return (
          <div className="space-y-3">
            <div className="flex items-center gap-3">
              {getStatusIcon()}
              <span className={classNames('font-medium', getStatusColor())}>
                {getStatusText()}
              </span>
            </div>
            <div className="bg-red-50 p-3 rounded-lg">
              <div className="text-sm text-red-700">
                <span className="font-medium">错误信息：</span>
                <div className="mt-1 p-2 bg-white rounded border text-red-600">
                  {resultMap.error || '未知错误'}
                </div>
              </div>
            </div>
          </div>
        );

      default:
        return (
          <div className="flex items-center gap-3">
            {getStatusIcon()}
            <span className={classNames('font-medium', getStatusColor())}>
              {getStatusText()}
            </span>
          </div>
        );
    }
  };

  return (
    <div className={classNames('workflow-status', className)}>
      {renderContent()}
    </div>
  );
};

export default WorkflowStatus;