import React, { useState } from 'react';
import { Popover } from 'antd';
import { agentModeList, defaultAgentMode, workflowTemplates, AgentTypeEnum } from '@/utils/constants';
import classNames from 'classnames';

interface AgentModeSelectorProps {
  value?: string;
  onChange?: (mode: string, template?: string) => void;
  size?: 'small' | 'medium' | 'large';
}

const AgentModeSelector: React.FC<AgentModeSelectorProps> = ({
  value = defaultAgentMode.key,
  onChange,
  size = 'medium'
}) => {
  const [selectedMode, setSelectedMode] = useState(value);
  const [selectedTemplate, setSelectedTemplate] = useState<string>('');
  const [popoverVisible, setPopoverVisible] = useState(false);

  const currentMode = agentModeList.find(mode => mode.key === selectedMode) || defaultAgentMode;
  const isWorkflowMode = currentMode.agentType === AgentTypeEnum.WORKFLOW;

  const handleModeSelect = (mode: any) => {
    setSelectedMode(mode.key);
    if (mode.agentType === AgentTypeEnum.WORKFLOW) {
      // 工作流模式，自动选择默认模板
      const defaultTemplate = workflowTemplates[0];
      setSelectedTemplate(defaultTemplate.id);
      onChange?.(mode.key, defaultTemplate.id);
    } else {
      // 其他模式
      setSelectedTemplate('');
      onChange?.(mode.key);
    }
    setPopoverVisible(false); // 对所有模式都关闭
  };

  const sizeClasses = {
    small: 'h-[24px] px-2 text-[12px]',
    medium: 'h-10 px-4 text-sm text-gray-800',
    large: 'h-12 px-4 text-lg'
  };

  const ModeSelector = () => (
    <div className="w-96 p-2">
      <div className="space-y-1">
        {agentModeList.map((mode) => (
          <div
            key={mode.key}
            className={classNames(
              'flex items-center p-3 rounded-md cursor-pointer transition-all hover:bg-gray-100',
              selectedMode === mode.key
                ? 'bg-blue-50 font-semibold'
                : 'hover:bg-gray-50'
            )}
            onClick={() => handleModeSelect(mode)}
          >
            <div className="flex-shrink-0 mr-3">
              <i className={`font_family ${mode.icon} ${mode.color} text-lg`}></i>
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-gray-800 text-sm">{mode.name}</div>
            </div>
            {selectedMode === mode.key && (
              <div className="flex-shrink-0">
                <i className="font_family icon-gou text-blue-500 text-base"></i>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );

  const displayText = () => {
    const currentMode = agentModeList.find(mode => mode.key === selectedMode) || defaultAgentMode;
    if (currentMode.agentType === AgentTypeEnum.WORKFLOW) {
      const template = workflowTemplates.find(t => t.id === selectedTemplate) || workflowTemplates[0];
      return `${currentMode.name} · ${template.name}`;
    }
    return currentMode.name;
  };

  return (
    <Popover
      content={<ModeSelector />}
      trigger="click"
      placement="bottomLeft"
      open={popoverVisible}
      onOpenChange={setPopoverVisible}
      overlayClassName="agent-mode-popover"
      getPopupContainer={(triggerNode) => triggerNode.parentElement || document.body}
    >
      <div
        className={classNames(
          'flex items-center justify-between rounded-[6px] cursor-pointer transition-colors bg-[#f4f4f9] hover:bg-[#efeff6] min-w-0',
          sizeClasses[size]
        )}
      >
        <div className="flex items-center min-w-0 flex-1">
          <i className={`font_family ${currentMode.icon} ${currentMode.color} mr-2 flex-shrink-0`}></i>
          <span className="truncate">{displayText()}</span>
        </div>
        <i className="font_family icon-xiala text-gray-400 ml-2"></i>
      </div>
    </Popover>
  );
};

export default AgentModeSelector;